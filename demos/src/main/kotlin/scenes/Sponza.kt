package scenes

import de.hanno.hpengine.Engine
import de.hanno.hpengine.world.addStaticModelEntity
import de.hanno.hpengine.world.loadScene

fun main() {

    val demoAndEngineConfig = createDemoAndEngineConfig()

    val engine = createEngine(demoAndEngineConfig)

    engine.runSponza()
}

internal fun Engine.runSponza() {
    world.loadScene {
        addStaticModelEntity("Sponza", "assets/models/sponza.obj")
    }
    simulate()
}

