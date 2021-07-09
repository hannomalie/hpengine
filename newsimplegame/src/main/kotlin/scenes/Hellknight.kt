package scenes

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.scene.scene
import de.hanno.hpengine.engine.transform.AABBData
import org.joml.Vector3f

val Engine.hellknightScene
    get() = scene("HellknightScene") {
        entities {
            entity("Hellknight") {
                modelComponent(
                    name = "Hellknight",
                    file = "doom3monster/monster.md5mesh",
                    textureManager = engineContext.backend.textureManager,
                    directory = engineContext.config.directories.gameDir,
                    aabb = AABBData(
                        Vector3f(-60f, -10f, -35f),
                        Vector3f(60f, 130f, 50f)
                    )
                )
            }
        }
    }