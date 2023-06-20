package com.example.android.camerax.video.effects

import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.VideoFrameProcessor
import androidx.media3.common.util.Log

/**
 * A [VideoFrameProcessor.Listener] implementation that puts out debug logs based for each callback.
 */
class SimpleVideoFrameProcessorListener : VideoFrameProcessor.Listener {
    override fun onOutputSizeChanged(width: Int, height: Int) {
        Log.d(TAG, "Output size changed")
    }

    override fun onOutputFrameAvailableForRendering(presentationTimeUs: Long) {
        Log.d(TAG, "Output frame available for rendering")
    }

    override fun onError(exception: VideoFrameProcessingException) {
        Log.d(TAG, "On error", exception)
    }

    override fun onEnded() {
        Log.d(TAG, "On ended")
    }

    companion object {
        private val TAG = SimpleVideoFrameProcessorListener::class.java.name
    }
}