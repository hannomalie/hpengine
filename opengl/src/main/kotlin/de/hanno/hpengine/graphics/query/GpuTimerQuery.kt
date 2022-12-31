package de.hanno.hpengine.graphics.query

import de.hanno.hpengine.graphics.GraphicsApi
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL33

class GpuTimerQuery(private val graphicsApi: GraphicsApi) : GpuQuery<Float?> {
    override val queryToWaitFor: Int = graphicsApi.onGpu { GL15.glGenQueries() }
    private val end: Int = graphicsApi.onGpu { GL15.glGenQueries() }

    private var finished = false
    private var started = false

    override fun begin() {
        finished = false
        graphicsApi.onGpu {
            GL33.glQueryCounter(queryToWaitFor, GL33.GL_TIMESTAMP)
        }
        started = true
    }

    override fun end() {
        check(started) { "Don't end a query before it was started!" }
        graphicsApi.onGpu {
            GL33.glQueryCounter(end, GL33.GL_TIMESTAMP)
        }
        finished = true
    }

    override fun resultsAvailable() = graphicsApi.onGpu {
        GL33.glGetQueryObjectui64(
            queryToWaitFor,
            GL15.GL_QUERY_RESULT_AVAILABLE
        )
    }.toInt() == GL11.GL_TRUE

    val timeTaken: Long get() = endTime - startTime
    val startTime: Long
        get() = graphicsApi.onGpu { GL33.glGetQueryObjectui64(queryToWaitFor, GL15.GL_QUERY_RESULT) }
    val endTime: Long
        get() = graphicsApi.onGpu { GL33.glGetQueryObjectui64(end, GL15.GL_QUERY_RESULT) }

    override val result: Float
        get() {
            check(finished) { "Don't query result before query is finished!" }
            while (!resultsAvailable()) { }
            return timeTaken / 1000000f
        }
}