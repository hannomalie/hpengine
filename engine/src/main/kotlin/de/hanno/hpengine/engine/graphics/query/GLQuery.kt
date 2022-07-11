package de.hanno.hpengine.engine.graphics.query

import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.backend.OpenGl
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL33

interface GLQuery<RESULT> {
    fun begin(): GLTimerQuery?
    fun end()
    fun resultsAvailable(gpuContext: GpuContext<*>): Boolean = gpuContext.invoke {
        GL33.glGetQueryObjectui64(
            queryToWaitFor,
            GL15.GL_QUERY_RESULT_AVAILABLE
        )
    }.toInt() == GL11.GL_TRUE

    val queryToWaitFor: Int
    val result: RESULT
}