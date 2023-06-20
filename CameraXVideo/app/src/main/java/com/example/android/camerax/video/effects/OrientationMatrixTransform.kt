package com.example.android.camerax.video.effects

import android.opengl.Matrix
import androidx.media3.common.util.Size
import androidx.media3.effect.GlMatrixTransformation

/**
 * A [GlMatrixTransformation] that samples the texture according to the orientation matrix and draws
 * onto the new texture.
 */
class OrientationMatrixTransform(
    cameraViewSpecificTransform: FloatArray,
    private val outputSize: Size
) : GlMatrixTransformation {
    // Adjusted orientation matrix for vertex shader usage
    private val adjustedOrientationMatrix = FloatArray(16)

    // Raw orientation matrix passed from SurfaceOutput
    private val cameraViewSpecificTransform = cameraViewSpecificTransform.clone()

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        /*
        In this configure we do a few operations to orient, crop and scale correctly

        The original transform matrix provided by Camera framework corrects for orientation

        Ultimately, let's represent this problem with a few parameters:
        <vertex_position, fragment_color>
        <gl_pos, f(A * alpha * gl_pos)> is the base Media3 vertex and fragment color combo by default
        gl_pos = vertex position
        A = transform matrix from camera framework
        alpha = transform matrix for texture coords from NDC coords
        f() = color function given a position

        CameraX does an additional transform to size the viewport, let's model it as so:
        CameraX GL Transform (abbreviated to GLT) = A * B
        Where B is the delta matrix

        We ultimately desire a vertex and fragment pair that is scaled with:
        <gl_pos, f(A * B * alpha * gl_pos)>

        But because we don't want to change the fragment code, we can only adjust the vertex gl_pos

        by subbing gl_pos => alpha_inv * B_inv * alpha * gl_pos, we achieve
        <alpha_inv * B_inv * alpha * gl_pos, f(A * alpha * gl_pos)>

        Which maintains the same color value but scaled vertex position correctly
        */

        // Adjust B coords with alpha (where alpha is the function of NDC -> Tex coord)
        Matrix.multiplyMM(
            adjustedOrientationMatrix,
            0,
            cameraViewSpecificTransform,
            0,
            TEXTURE_FROM_NDC,
            0
        )
        Matrix.multiplyMM(
            adjustedOrientationMatrix,
            0,
            NDC_FROM_TEXTURE,
            0,
            adjustedOrientationMatrix,
            0
        )

        // Invert that final matrix
        Matrix.invertM(adjustedOrientationMatrix, 0, adjustedOrientationMatrix, 0)
        return outputSize
    }

    override fun getGlMatrixArray(presentationTimeUs: Long): FloatArray {
        return adjustedOrientationMatrix
    }

    companion object {
        // Orientation matrix operates in [0, 1] but NDC is in [-1, 1]
        // 4x4 matrix stored in column-major order
        private val TEXTURE_FROM_NDC =
            floatArrayOf(
                0.5f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.5f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                1.0f,
                0.0f,
                0.5f,
                0.5f,
                0.0f,
                1.0f
            )
        // 4x4 matrix stored in column-major order
        private val NDC_FROM_TEXTURE =
            floatArrayOf(
                2.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                2.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                1.0f,
                0.0f,
                -1.0f,
                -1.0f,
                0.0f,
                1.0f
            )
    }
}