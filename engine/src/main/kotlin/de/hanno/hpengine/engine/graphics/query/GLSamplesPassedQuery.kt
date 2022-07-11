package de.hanno.hpengine.engine.graphics.query

import de.hanno.hpengine.engine.graphics.GpuContext
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL33

class GLSamplesPassedQuery(gpuContext: GpuContext<*>) : GLQuery<Int?> {
    override val queryToWaitFor: Int
    private val target = GL15.GL_SAMPLES_PASSED
    private val gpuContext: GpuContext<*>

    @Volatile
    private var finished = false
    private var started = false
    override fun begin(): GLTimerQuery? {
        gpuContext.invoke {
            GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED, queryToWaitFor)
            Unit
        }
        started = true
        return null
    }

    override fun end() {
        check(started) { "Don't end a query before it was started!" }
        gpuContext.invoke {
            GL15.glEndQuery(target)
            Unit
        }
        finished = true
    }

    override val result: Int
        get() {
            check(finished) { "Don't query result before query is finished!" }
            while (!resultsAvailable(gpuContext)) {
            }
            return gpuContext.invoke { GL33.glGetQueryObjectui64(queryToWaitFor, GL15.GL_QUERY_RESULT).toInt() }
        }

    init {
        this.gpuContext = gpuContext
        queryToWaitFor = this.gpuContext.invoke { GL15.glGenQueries() }
    }
}