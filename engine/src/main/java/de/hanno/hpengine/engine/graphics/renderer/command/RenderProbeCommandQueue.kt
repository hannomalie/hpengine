package de.hanno.hpengine.engine.graphics.renderer.command

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.scene.EnvironmentProbe
import java.util.Optional
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class RenderProbeCommandQueue {
    private val workQueue: BlockingQueue<RenderProbeCommand>
    @JvmOverloads
    fun addProbeRenderCommand(probe: EnvironmentProbe?, urgent: Boolean = false) {
        if (!urgent) {
            val command = RenderProbeCommand(probe)
            if (contains(command)) {
                return
            }
            add(RenderProbeCommand(probe))
        } else {
            add(RenderProbeCommand(probe))
        }
    }

    private operator fun contains(command: RenderProbeCommand): Boolean {
        var result = false
        for (c in workQueue) {
            if (c.getProbe() == command.getProbe()) {
                result = true
            }
        }
        return result
    }

    private fun add(command: RenderProbeCommand) {
        if (getRemaining() > 20) {
            return
        }
        workQueue.add(command)
    }

    fun take(): Optional<RenderProbeCommand> {
        if (!workQueue.isEmpty()) {
            val command = workQueue.poll()
            if (command != null) {
                return Optional.of(command)
            }
        }
        return Optional.empty()
    }

    fun takeNearest(camera: Entity?): Optional<RenderProbeCommand> {
        return workQueue.stream().findFirst()
    }

    fun getRemaining(): Int {
        return workQueue.size
    }

    companion object {
        @Volatile
        var MAX_PROBES_RENDERED_PER_DRAW_CALL = 2
    }

    init {
        workQueue = LinkedBlockingQueue()
    }
}