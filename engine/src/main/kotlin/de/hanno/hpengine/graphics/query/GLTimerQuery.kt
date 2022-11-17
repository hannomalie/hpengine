package de.hanno.hpengine.graphics.query

import de.hanno.hpengine.graphics.GpuContext
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL33

class GLTimerQuery(gpuContext: GpuContext) : GLQuery<Float?> {
    private val gpuContext: GpuContext
    override val queryToWaitFor: Int
    private val end: Int

    @Volatile
    private var finished = false
    private var started = false
    private fun glGenQuery(): Int {
        return gpuContext.onGpu { GL15.glGenQueries() }
    }

    override fun begin(): GLTimerQuery? {
        finished = false
        gpuContext.onGpu {
            GL33.glQueryCounter(queryToWaitFor, GL33.GL_TIMESTAMP)
            Unit
        }
        started = true
        return this
    }

    override fun end() {
        check(started) { "Don't end a query before it was started!" }
        gpuContext.onGpu {
            GL33.glQueryCounter(end, GL33.GL_TIMESTAMP)
            Unit
        }
        finished = true
    }

    val timeTaken: Long
        get() = endTime - startTime
    val startTime: Long
        get() = gpuContext.onGpu { GL33.glGetQueryObjectui64(queryToWaitFor, GL15.GL_QUERY_RESULT) }
    val endTime: Long
        get() = gpuContext.onGpu { GL33.glGetQueryObjectui64(end, GL15.GL_QUERY_RESULT) }
    override val result: Float
        get() {
            check(finished) { "Don't query result before query is finished!" }
            while (!resultsAvailable(gpuContext)) {
            }
            return timeTaken / 1000000f
        }

    init {
        this.gpuContext = gpuContext
        queryToWaitFor = glGenQuery()
        end = glGenQuery()
    }
}