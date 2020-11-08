package scenes

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.CustomComponent.Companion.customComponent
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.scene.scene
import de.hanno.hpengine.engine.textureManager
import de.hanno.hpengine.engine.transform.AABBData
import org.joml.Vector3f

val Engine.sponzaScene
    get() = scene("SponzaScene") {
        entities {
//            entity("Sponza") {
//                modelComponent(
//                    name = "Sponza",
//                    file = "assets/models/sponza.obj",
//                    materialManager = scene.materialManager,
//                    modelComponentManager = scene.modelComponentManager,
//                    gameDirectory = engineContext.config.directories.gameDir
//                )
//            }

            entity("Sphere") {
                modelComponent(
                    name = "Sphere",
                    file = "assets/models/sphere.obj",
                    materialManager = scene.materialManager,
                    modelComponentManager = scene.modelComponentManager,
                    gameDirectory = engineContext.config.directories.gameDir
                )
                customComponent { scene, deltaSeconds ->
                    if(this@entity.transform.position.x > 100f) {
                        this@entity.transform.translation(0f, 0f, 0f)
                    }
                    this@entity.transform.translate(Vector3f(deltaSeconds * 20f, 0f, 0f))
                }
            }
        }
    }