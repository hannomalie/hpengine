package de.hanno.hpengine.graphics.fps

import com.artemis.BaseSystem
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.RenderSystem

class FPSCounterSystem(val fpsCounter: FPSCounter = FPSCounter()): RenderSystem {
    override fun render(renderState: RenderState) {
        fpsCounter.update()
    }
}


class CPSCounterSystem: BaseSystem() {
    val fpsCounter: CPSCounter = CPSCounter()

    override fun processSystem() {
        fpsCounter.update()
    }
}