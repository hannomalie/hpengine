package scenes

import de.hanno.hpengine.Engine
import de.hanno.hpengine.transform.AABBData
import de.hanno.hpengine.world.addAnimatedModelEntity
import de.hanno.hpengine.world.addStaticModelEntity
import de.hanno.hpengine.world.loadScene
import org.joml.Vector3f
import java.util.concurrent.CompletableFuture

fun main() {
    val demoAndEngineConfig = createDemoAndEngineConfig()

    val engine = createEngine(demoAndEngineConfig)

    engine.runMultipleObjects()
}

fun Engine.runMultipleObjects() {
    world.loadScene {
        addStaticModelEntity("Sponza", "assets/models/sponza.obj")
        addStaticModelEntity("Ferrari", "assets/models/ferrari.obj", translation = Vector3f(100f, 10f, 0f))
        addAnimatedModelEntity("Hellknight",
            "assets/models/doom3monster/monster.md5mesh",
            AABBData(
                Vector3f(-60f, -10f, -35f),
                Vector3f(60f, 130f, 50f)
            )
        )
        addAnimatedModelEntity("Bob",
            "assets/models/bob_lamp_update/bob_lamp_update_export.md5mesh",
            AABBData( // This is not accurate, but big enough to not cause culling problems
                Vector3f(-60f, -10f, -35f),
                Vector3f(60f, 130f, 50f)
            )
        )
    }
    simulate()
}

