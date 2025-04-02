package scenes

import com.artemis.BaseSystem
import de.hanno.hpengine.Engine
import de.hanno.hpengine.graphics.editor.EditorInput
import de.hanno.hpengine.world.addStaticModelEntity
import de.hanno.hpengine.world.loadScene
import org.koin.core.module.Module
import org.koin.dsl.binds
import org.koin.dsl.module

fun main() {

    val demoAndEngineConfig = createDemoAndEngineConfig(Demo.Sponza)

    val engine = createEngine(demoAndEngineConfig)

    engine.runSponza()
}

internal fun Engine.runSponza() {
    world.loadScene {
        addPrimaryCameraControls()
        addStaticModelEntity("Sponza", "assets/models/sponza.obj")
    }
    simulate()
}


