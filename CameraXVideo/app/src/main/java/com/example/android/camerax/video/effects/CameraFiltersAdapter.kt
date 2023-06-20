package com.example.android.camerax.video.effects

import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceProcessor
import java.util.concurrent.Executor

/**
 * A [CameraEffect] that wraps around the Surface processor, allowing for a direct call to release
 * if needed.
 */
class CameraFiltersAdapter(
    targets: Int,
    processorExecutor: Executor,
    surfaceProcessor: SurfaceProcessor
) : CameraEffect(targets, processorExecutor, surfaceProcessor, {}) {
    fun release() {
        val glSurfaceProcessor = surfaceProcessor
        if (glSurfaceProcessor is CameraFiltersAdapterProcessor) {
            glSurfaceProcessor.release()
        }
    }
}