package de.hanno.hpengine.graphics.fps

import com.artemis.BaseSystem
import com.artemis.World
import de.hanno.hpengine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderSystem

class FPSCounterSystem(val fpsCounter: FPSCounter = FPSCounter()): RenderSystem {
    override fun render(result: DrawResult, renderState: RenderState) {
        fpsCounter.update()
    }
    override lateinit var artemisWorld: World
}


class CPSCounterSystem: BaseSystem() {
    val fpsCounter: CPSCounter = CPSCounter()

    override fun processSystem() {
        fpsCounter.update()
    }
}