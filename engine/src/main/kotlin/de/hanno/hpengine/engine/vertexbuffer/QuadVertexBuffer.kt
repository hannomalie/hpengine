package de.hanno.hpengine.engine.vertexbuffer

import de.hanno.hpengine.engine.graphics.GpuContext
import java.util.*
import javax.vecmath.Vector2f

class QuadVertexBuffer : VertexBuffer {
    constructor(gpuContext: GpuContext<*>?, fullscreen: Boolean) : super(
        gpuContext!!,
        EnumSet.of<DataChannels>(DataChannels.POSITION3, DataChannels.TEXCOORD),
        getPositionsAndTexCoords(fullscreen)
    ) {
    }

    constructor(gpuContext: GpuContext<*>?, leftBottom: Vector2f, rightUpper: Vector2f) : super(
        gpuContext!!,
        EnumSet.of<DataChannels>(DataChannels.POSITION3, DataChannels.TEXCOORD),
        getPositionsAndTexCoords(leftBottom, rightUpper)
    ) {
    }

    companion object {
        fun getPositionsAndTexCoords(fullscreen: Boolean): FloatArray {
            return if (fullscreen) {
                floatArrayOf(
                    -1.0f, -1.0f, 0.0f, 0f, 0f,
                    1.0f, -1.0f, 0.0f, 1f, 0f,
                    -1.0f, 1.0f, 0.0f, 0f, 1.0f,
                    -1.0f, 1.0f, 0.0f, 0f, 1.0f,
                    1.0f, -1.0f, 0.0f, 1.0f, 0f,
                    1.0f, 1.0f, 0.0f, 1.0f, 1.0f
                )
            } else {
//			return new float[] {
//					-1.0f, -1.0f, 0.0f, 0f, 0f,
//					0f, -1.0f, 0.0f,    1f, 0f,
//					-1.0f,  0f, 0.0f,   0f, 1.0f,
//					-1.0f,  0f, 0.0f,   0f, 1.0f,
//					0f, -1.0f, 0.0f,    1.0f, 0f,
//					0f,  0f, 0.0f,      1.0f, 1.0f
//			};
                getPositionsAndTexCoords(
                    Vector2f(-1f, -1f),
                    Vector2f(0f, 0f)
                )
            }
        }

        fun getPositionsAndTexCoords(leftBottom: Vector2f, rightUpper: Vector2f): FloatArray {
            return floatArrayOf(
                leftBottom.x, leftBottom.y, 0.0f, 0f, 0f,
                rightUpper.x, leftBottom.y, 0.0f, 1f, 0f,
                leftBottom.x, rightUpper.y, 0.0f, 0f, 1.0f,
                leftBottom.x, rightUpper.y, 0.0f, 0f, 1.0f,
                rightUpper.x, leftBottom.y, 0.0f, 1.0f, 0f,
                rightUpper.x, rightUpper.y, 0.0f, 1.0f, 1.0f
            )
        }
    }
}