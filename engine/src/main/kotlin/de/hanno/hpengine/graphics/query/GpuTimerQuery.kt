package de.hanno.hpengine.graphics.query

import de.hanno.hpengine.graphics.GpuContext
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL33

class GpuTimerQuery(private val gpuContext: GpuContext) : GpuQuery<Float?> {
    override val queryToWaitFor: Int = gpuContext.onGpu { GL15.glGenQueries() }
    private val end: Int = gpuContext.onGpu { GL15.glGenQueries() }

    private var finished = false
    private var started = false

    override fun begin() {
        finished = false
        gpuContext.onGpu {
            GL33.glQueryCounter(queryToWaitFor, GL33.GL_TIMESTAMP)
        }
        started = true
    }

    override fun end() {
        check(started) { "Don't end a query before it was started!" }
        gpuContext.onGpu {
            GL33.glQueryCounter(end, GL33.GL_TIMESTAMP)
        }
        finished = true
    }

    override fun resultsAvailable() = gpuContext.onGpu {
        GL33.glGetQueryObjectui64(
            queryToWaitFor,
            GL15.GL_QUERY_RESULT_AVAILABLE
        )
    }.toInt() == GL11.GL_TRUE

    val timeTaken: Long get() = endTime - startTime
    val startTime: Long
        get() = gpuContext.onGpu { GL33.glGetQueryObjectui64(queryToWaitFor, GL15.GL_QUERY_RESULT) }
    val endTime: Long
        get() = gpuContext.onGpu { GL33.glGetQueryObjectui64(end, GL15.GL_QUERY_RESULT) }

    override val result: Float
        get() {
            check(finished) { "Don't query result before query is finished!" }
            while (!resultsAvailable()) { }
            return timeTaken / 1000000f
        }
}