package scenes

import de.hanno.hpengine.Engine
import de.hanno.hpengine.world.addStaticModelEntity
import de.hanno.hpengine.world.loadScene

fun main() {

    val demoAndEngineConfig = createDemoAndEngineConfig()

    val engine = createEngine(demoAndEngineConfig)

    engine.runSkyBox()
}

internal fun Engine.runSkyBox() {
    world.loadScene {  }
    simulate()
}

