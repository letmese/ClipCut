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

            // Drain queued samples
            for (sample in pendingVideoSamples) {
                muxer.writeSampleData(videoTrackIndex, sample.buffer, sample.bufferInfo)
            }
            pendingVideoSamples.clear()

            for (sample in pendingAudioSamples) {
                muxer.writeSampleData(audioTrackIndex, sample.buffer, sample.bufferInfo)
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

                val infoCopy = MediaCodec.BufferInfo().apply {
                    set(0, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                }
                pendingVideoSamples.add(PendingSample(copy, infoCopy))
                return
            }
            muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo)
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

                val infoCopy = MediaCodec.BufferInfo().apply {
                    set(0, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                }
                pendingAudioSamples.add(PendingSample(copy, infoCopy))
                return
            }
            muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo)
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
