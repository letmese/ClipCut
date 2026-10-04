package com.clipcut.core.codec

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import com.clipcut.core.log.AppLog
import java.nio.ByteBuffer

class MediaMuxerWrapper(
    outputPath: String,
    private val expectedTrackCount: Int = 2
) {
    private val muxer: MediaMuxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var addedTracks = 0
    private var isStarted = false
    private val lock = Object()

    private val pendingVideoSamples = mutableListOf<PendingSample>()
    private val pendingAudioSamples = mutableListOf<PendingSample>()

    private var lastVideoPtsUs = -1L
    private var lastAudioPtsUs = -1L

    private data class PendingSample(
        val buffer: ByteBuffer,
        val bufferInfo: MediaCodec.BufferInfo
    )

    fun addVideoTrack(format: MediaFormat): Int {
        synchronized(lock) {
            if (videoTrackIndex != -1) {
                AppLog.w(TAG, "Video track already added")
                return videoTrackIndex
            }
            videoTrackIndex = muxer.addTrack(format)
            addedTracks++
            AppLog.i(TAG, "Added video track index $videoTrackIndex (tracks ready: $addedTracks/$expectedTrackCount)")
            checkStartMuxerLocked()
            return videoTrackIndex
        }
    }

    fun addAudioTrack(format: MediaFormat): Int {
        synchronized(lock) {
            if (audioTrackIndex != -1) {
                AppLog.w(TAG, "Audio track already added")
                return audioTrackIndex
            }
            audioTrackIndex = muxer.addTrack(format)
            addedTracks++
            AppLog.i(TAG, "Added audio track index $audioTrackIndex (tracks ready: $addedTracks/$expectedTrackCount)")
            checkStartMuxerLocked()
            return audioTrackIndex
        }
    }

    private fun checkStartMuxerLocked() {
        if (!isStarted && addedTracks >= expectedTrackCount) {
            muxer.start()
            isStarted = true
            AppLog.i(TAG, "MediaMuxer started successfully")

            // Drain queued video samples in monotonic PTS order
            pendingVideoSamples.sortBy { it.bufferInfo.presentationTimeUs }
            for (sample in pendingVideoSamples) {
                val info = copyBufferInfo(sample.bufferInfo)
                if (info.presentationTimeUs <= lastVideoPtsUs) {
                    info.presentationTimeUs = lastVideoPtsUs + 1L
                }
                lastVideoPtsUs = info.presentationTimeUs
                muxer.writeSampleData(videoTrackIndex, sample.buffer, info)
            }
            pendingVideoSamples.clear()

            // Drain queued audio samples in monotonic PTS order
            pendingAudioSamples.sortBy { it.bufferInfo.presentationTimeUs }
            for (sample in pendingAudioSamples) {
                val info = copyBufferInfo(sample.bufferInfo)
                if (info.presentationTimeUs <= lastAudioPtsUs) {
                    info.presentationTimeUs = lastAudioPtsUs + 1L
                }
                lastAudioPtsUs = info.presentationTimeUs
                muxer.writeSampleData(audioTrackIndex, sample.buffer, info)
            }
            pendingAudioSamples.clear()
        }
    }

    fun writeVideoSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        synchronized(lock) {
            if (!isStarted) {
                val copy = ByteBuffer.allocateDirect(bufferInfo.size)
                buffer.position(bufferInfo.offset)
                buffer.limit(bufferInfo.offset + bufferInfo.size)
                copy.put(buffer)
                copy.flip()

                val infoCopy = copyBufferInfo(bufferInfo)
                pendingVideoSamples.add(PendingSample(copy, infoCopy))
                return
            }

            val info = copyBufferInfo(bufferInfo)
            if (info.presentationTimeUs <= lastVideoPtsUs) {
                info.presentationTimeUs = lastVideoPtsUs + 1L
            }
            lastVideoPtsUs = info.presentationTimeUs
            muxer.writeSampleData(videoTrackIndex, buffer, info)
        }
    }

    fun writeAudioSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        synchronized(lock) {
            if (!isStarted) {
                val copy = ByteBuffer.allocateDirect(bufferInfo.size)
                buffer.position(bufferInfo.offset)
                buffer.limit(bufferInfo.offset + bufferInfo.size)
                copy.put(buffer)
                copy.flip()

                val infoCopy = copyBufferInfo(bufferInfo)
                pendingAudioSamples.add(PendingSample(copy, infoCopy))
                return
            }

            val info = copyBufferInfo(bufferInfo)
            if (info.presentationTimeUs <= lastAudioPtsUs) {
                info.presentationTimeUs = lastAudioPtsUs + 1L
            }
            lastAudioPtsUs = info.presentationTimeUs
            muxer.writeSampleData(audioTrackIndex, buffer, info)
        }
    }

    private fun copyBufferInfo(src: MediaCodec.BufferInfo): MediaCodec.BufferInfo {
        return MediaCodec.BufferInfo().apply {
            set(src.offset, src.size, src.presentationTimeUs, src.flags)
        }
    }

    fun stopAndRelease() {
        synchronized(lock) {
            if (isStarted) {
                try {
                    muxer.stop()
                    AppLog.i(TAG, "MediaMuxer stopped normally")
                } catch (e: Exception) {
                    AppLog.e(TAG, "Exception while stopping MediaMuxer", e)
                }
                isStarted = false
            }
            try {
                muxer.release()
                AppLog.i(TAG, "MediaMuxer released")
            } catch (e: Exception) {
                AppLog.e(TAG, "Exception while releasing MediaMuxer", e)
            }
        }
    }

    companion object {
        private const val TAG = "MediaMuxerWrapper"
    }
}
