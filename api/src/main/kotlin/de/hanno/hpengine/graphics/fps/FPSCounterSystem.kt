package de.hanno.hpengine.graphics.fps

import com.artemis.BaseSystem
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.RenderSystem
import org.apache.logging.log4j.LogManager
import org.koin.core.annotation.Single

class FPSCounterSystem(val fpsCounter: FPSCounter = FPSCounter()): RenderSystem {
    private val logger = LogManager.getLogger(FPSCounterSystem::class.java)
    init {
        logger.info("Creating system")
    }
    override fun render(renderState: RenderState) {
        fpsCounter.update()
    }
}

class CPSCounterSystem: BaseSystem() {
    private val logger = LogManager.getLogger(CPSCounterSystem::class.java)
    init {
        logger.info("Creating system")
    }
    val fpsCounter: CPSCounter = CPSCounter()

    override fun processSystem() {
        fpsCounter.update()
    }
}