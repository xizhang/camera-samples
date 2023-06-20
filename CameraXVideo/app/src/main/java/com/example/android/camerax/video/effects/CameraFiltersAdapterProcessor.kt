package com.example.android.camerax.video.effects

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.Matrix
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.impl.utils.Threads
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import androidx.media3.common.*
import androidx.media3.common.util.Log
import androidx.media3.common.util.Size
import androidx.media3.effect.DefaultVideoFrameProcessor
import java.util.concurrent.Executor

/**
 * A [SurfaceProcessor] implementation that provides surface from GL Effects Frame Processor for
 * [SurfaceRequest] fulfillment. The surface processor also interacts with the GL processor to
 * indicate when the surface should be closed and no longer written to.
 *
 * In the lifecycle output surface provided can occur before input surface request, and thus the
 * adapter must be flexible for either to occur before the other.
 *
 * In the unconnected state, the CameraFiltersAdapterProcessor waits for both an input and output
 * before transitioning to the connected state.
 *
 * In the connected state, any change will try to disconnect: new input/output or connected
 * input/output becoming invalid.
 */
@SuppressLint("RestrictedApi")
class CameraFiltersAdapterProcessor(
    // Necessary GLEffectsFrameProcessor parameters
    private val context: Context,
    private val effects: List<Effect>,
    private val debugViewProvider: DebugViewProvider,
    private val inputColorInfo: ColorInfo,
    private val outputColorInfo: ColorInfo,
    private val renderFramesAutomatically: Boolean,
    private val listenerExecutor: Executor,
    private val listener: VideoFrameProcessor.Listener
) : SurfaceProcessor {
    // Track all active processors
    private val allActiveProcessors: MutableSet<DefaultVideoFrameProcessor> = HashSet()

    // Frame Processor and Associated Surface Output
    private var connectedRequest: SurfaceRequest? = null
    private var connectedOutput: SurfaceOutput? = null
    private var frameProcessor: DefaultVideoFrameProcessor? = null

    // Pending surface request and surface output
    private var pendingRequest: SurfaceRequest? = null
    private var pendingOutput: SurfaceOutput? = null

    override fun onInputSurface(request: SurfaceRequest) {
        // Create Texture, SurfaceTexture, and Surface
        Threads.checkMainThread()
        pendingRequest = request
        disconnectProcessor(frameProcessor, connectedRequest)
        tryConnectPendingProcessor()
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        Threads.checkMainThread()
        pendingOutput = surfaceOutput
        disconnectProcessor(frameProcessor, connectedRequest)
        tryConnectPendingProcessor()
    }

    private fun disconnectProcessor(
        defaultVideoFrameProcessor: DefaultVideoFrameProcessor?,
        request: SurfaceRequest?
    ) {
        if (
            defaultVideoFrameProcessor != null && allActiveProcessors.contains(defaultVideoFrameProcessor)
        ) {
            allActiveProcessors.remove(defaultVideoFrameProcessor)
            defaultVideoFrameProcessor.release()
        }
        request?.invalidate()
    }

    private fun tryConnectPendingProcessor() {
        Threads.checkMainThread()
        val request = pendingRequest
        // SurfaceOutput can be recycled.
        val output = if (pendingOutput != null) pendingOutput else connectedOutput
        if (request != null && output != null) {
            connectPendingProcessor(request, output)
        }
    }

    private fun connectPendingProcessor(request: SurfaceRequest, output: SurfaceOutput) {
        // Create the new processor
        val orientationMatrixTransform: OrientationMatrixTransform
        val outputSize = output.size
        val newFrameProcessor =
            try {
                // Copy orientation matrix to transform
                val identityMatrix = FloatArray(16)
                Matrix.setIdentityM(identityMatrix, 0)
                val cameraViewSpecificTransform = FloatArray(16)
                output.updateTransformMatrix(cameraViewSpecificTransform, identityMatrix)
                orientationMatrixTransform =
                    OrientationMatrixTransform(
                        cameraViewSpecificTransform,
                        Size(outputSize.width, outputSize.height)
                    )
                val effectsWithOrientation = listOf(orientationMatrixTransform, *effects.toTypedArray())
                DefaultVideoFrameProcessor.Factory.Builder()
                    .build()
                    .create(
                        context,
                        effectsWithOrientation,
                        debugViewProvider,
                        inputColorInfo,
                        outputColorInfo,
                        renderFramesAutomatically,
                        listenerExecutor,
                        listener
                    )
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
                disconnectProcessor(frameProcessor, request)
                return
            }

        frameProcessor = newFrameProcessor
        newFrameProcessor.registerInputStream(VideoFrameProcessor.INPUT_TYPE_SURFACE)
        allActiveProcessors.add(newFrameProcessor)

        // Set input format for the processor
        // Camera Pixels are expected to be square
        val surfaceRequestSize = request.resolution
        val frameInfo = FrameInfo.Builder(surfaceRequestSize.width, surfaceRequestSize.height).build()
        newFrameProcessor.setInputFrameInfo(frameInfo)

        // Camera currently isn't overriding buffer size, so set the default input buffer size
        newFrameProcessor.setInputDefaultBufferSize(surfaceRequestSize.width, surfaceRequestSize.height)

        // Prove input service
        val inputSurface = newFrameProcessor.inputSurface
        request.provideSurface(inputSurface, CameraXExecutors.mainThreadExecutor()) { result ->
            Log.d(TAG, "Accept input surface release result $result")
            disconnectProcessor(frameProcessor, request)
        }
        connectedRequest = request
        Log.d(
            TAG,
            "Configuring input frames with : Width= ${surfaceRequestSize.width} & Height= ${surfaceRequestSize.height}"
        )

        // TODO (b/265336561): cleanup hack
        repeat(5000) { newFrameProcessor.registerInputFrame() }

        // Bind the output surface to frame processor
        val outputSurface =
            output.getSurface(CameraXExecutors.mainThreadExecutor()) { event
                -> // Close the surface output
                Log.d(TAG, "SurfaceOutput result, release and close output")
                disconnectProcessor(frameProcessor, request)
                event.surfaceOutput.close()
            }

        // TODO (b/265336809): Do we want to use effects orientation ?
        val surfaceOutputInfo =
            SurfaceInfo(outputSurface, outputSize.width, outputSize.height, /* orientationDegrees= */ 0)
        newFrameProcessor.setOutputSurfaceInfo(surfaceOutputInfo)
        connectedOutput = output
        Log.d(
            TAG,
            "Configuring output surface info : Width= ${outputSize.width} & Height= ${outputSize.height}"
        )

        // Pending variables have been used
        pendingRequest = null
        pendingOutput = null
    }

    // Not intended to be used internally by the camera filters adapter processor, just for lifecycle
    fun release() {
        Threads.checkMainThread()
        Log.d(TAG, "Release")
        // We need only release the latest processor
        disconnectProcessor(frameProcessor, connectedRequest)
    }

    companion object {
        private val TAG = CameraFiltersAdapterProcessor::class.java.simpleName
    }
}