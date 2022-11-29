package de.hanno.hpengine.graphics.query

import de.hanno.hpengine.graphics.GpuContext
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL33

class GpuSamplesPassedQuery(private val gpuContext: GpuContext) : GpuQuery<Int> {
    override val queryToWaitFor: Int = gpuContext.onGpu { GL15.glGenQueries() }
    private val target = GL15.GL_SAMPLES_PASSED

    private var finished = false
    private var started = false

    override fun begin() {
        gpuContext.onGpu {
            GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED, queryToWaitFor)
        }
        started = true
    }

    override fun end() {
        check(started) { "Don't end a query before it was started!" }
        gpuContext.onGpu {
            GL15.glEndQuery(target)
        }
        finished = true
    }

    override fun resultsAvailable() = gpuContext.onGpu {
        GL33.glGetQueryObjectui64(
            queryToWaitFor,
            GL15.GL_QUERY_RESULT_AVAILABLE
        )
    }.toInt() == GL11.GL_TRUE

    override val result: Int
        get() {
            check(finished) { "Don't query result before query is finished!" }
            while (!resultsAvailable()) { }
            return gpuContext.onGpu {
                GL33.glGetQueryObjectui64(queryToWaitFor, GL15.GL_QUERY_RESULT).toInt()
            }
        }
}