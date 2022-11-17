package de.hanno.hpengine.graphics.query

import de.hanno.hpengine.graphics.GpuContext
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL33

interface GLQuery<RESULT> {
    fun begin(): GLTimerQuery?
    fun end()
    fun resultsAvailable(gpuContext: GpuContext): Boolean = gpuContext.onGpu {
        GL33.glGetQueryObjectui64(
            queryToWaitFor,
            GL15.GL_QUERY_RESULT_AVAILABLE
        )
    }.toInt() == GL11.GL_TRUE

    val queryToWaitFor: Int
    val result: RESULT
}