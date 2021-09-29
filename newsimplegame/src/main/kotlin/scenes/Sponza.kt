package scenes

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.scene.dsl.CustomComponentDescription
import de.hanno.hpengine.engine.scene.dsl.scene
import de.hanno.hpengine.engine.scene.dsl.Directory
import de.hanno.hpengine.engine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.engine.scene.dsl.entity
import org.joml.Vector3f

val Engine.sponzaScene
    get() = scene("SponzaScene") {
        entity("Sponza") {
            add(
                StaticModelComponentDescription(
                    "assets/models/sponza.obj",
                    Directory.Game,
                )
            )
        }

        entity("Sphere") {
            add(
                StaticModelComponentDescription(
                    "assets/models/sphere.obj",
                    Directory.Game,
                )
            )
            add(
                CustomComponentDescription { scene, entity, deltaSeconds ->
                    val transform = entity.transform
                    if(transform.position.x > 100f) {
                        transform.translation(0f, 0f, 0f)
                    }
                    transform.translate(Vector3f(deltaSeconds * 20f, 0f, 0f))
                }
            )
        }
//      TODO: Implement syntax
//        entity("Probe0") {
//            addComponent(ReflectionProbe(Vector3f(100f), this))
//        }
//        entity("Probe1") {
//            addComponent(ReflectionProbe(Vector3f(200f), this))
//        }
    }