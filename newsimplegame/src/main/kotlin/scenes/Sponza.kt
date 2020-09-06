package scenes

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.CustomComponent.Companion.customComponent
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.scene.scene
import de.hanno.hpengine.engine.transform.AABBData
import org.joml.Vector3f

val Engine.sponzaScene
    get() = scene("SponzaScene") {
        entities {
            entity("Sponza") {
                modelComponent(
                    name = "Sponza",
                    file = "assets/models/sponza.obj",
                    materialManager = scene.materialManager,
                    gameDirectory = engineContext.config.directories.gameDir
                )
            }
        }
    }