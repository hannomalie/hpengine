package scenes

import de.hanno.hpengine.Engine
import de.hanno.hpengine.world.loadScene
import org.koin.core.module.Module

fun main() {

    val demoAndEngineConfig = createDemoAndEngineConfig(Demo.SkyBox, emptyList<Module>())

    val engine = createEngine(demoAndEngineConfig)

    engine.runSkyBox()
}

internal fun Engine.runSkyBox() {
    world.loadScene {  }
    simulate()
}

