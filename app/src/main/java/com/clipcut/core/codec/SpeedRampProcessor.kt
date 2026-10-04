package com.clipcut.core.codec

import com.clipcut.core.model.SpeedRamp

object SpeedRampProcessor {

    fun sourceToTimeline(sourceOffsetUs: Long, ramp: SpeedRamp, constantSpeed: Float): Long {
        if (!ramp.isActive) return (sourceOffsetUs / constantSpeed).toLong()
        val sorted = ramp.keyframes.sortedBy { it.timeOffsetUs }
        var timelineUs = 0L
        var sourceUs = 0L
        val stepUs = 10_000L
        while (sourceUs < sourceOffsetUs) {
            val nextSource = minOf(sourceUs + stepUs, sourceOffsetUs)
            val midSource = (sourceUs + nextSource) / 2
            val speed = ramp.speedAt(midSource)
            val sourceDelta = nextSource - sourceUs
            timelineUs += (sourceDelta / speed).toLong()
            sourceUs = nextSource
        }
        return timelineUs
    }

    fun timelineToSource(timelineOffsetUs: Long, ramp: SpeedRamp, constantSpeed: Float): Long {
        if (!ramp.isActive) return (timelineOffsetUs * constantSpeed).toLong()
        var sourceUs = 0L
        var timelineUs = 0L
        val stepUs = 10_000L
        while (timelineUs < timelineOffsetUs) {
            val speed = ramp.speedAt(sourceUs)
            val timelineDelta = (stepUs / speed).toLong()
            if (timelineUs + timelineDelta > timelineOffsetUs) {
                val frac = (timelineOffsetUs - timelineUs).toFloat() / timelineDelta
                sourceUs += (stepUs * frac).toLong()
                break
            }
            sourceUs += stepUs
            timelineUs += timelineDelta
        }
        return sourceUs
    }
}
