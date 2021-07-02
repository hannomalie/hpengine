package scenes

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.programManager
import de.hanno.hpengine.engine.component.CustomComponent.Companion.customComponent
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.graphics.renderer.extensions.ReflectionProbe
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.scene.scene
import org.joml.Vector3f

val Engine.sponzaScene
    get() = scene("SponzaScene") {
        entities {
            entity("Sponza") {
                modelComponent(
                    name = "Sponza",
                    file = "assets/models/sponza.obj",
                    materialManager = engineContext.extensions.materialExtension.manager,
                    gameDirectory = engineContext.config.directories.gameDir
                ).apply {
                    val bricksMaterial = materials.first { it.name == "bricks" } as SimpleMaterial
                    bricksMaterial.materialInfo.program = engineContext.programManager.heightMappingFirstPassProgram
                    bricksMaterial.materialInfo = bricksMaterial.materialInfo
                            .copy(parallaxScale = 0.3f, parallaxBias = 0.3f)
                }
            }

            entity("Sphere") {
                modelComponent(
                    name = "Sphere",
                    file = "assets/models/sphere.obj",
                    materialManager = engineContext.extensions.materialExtension.manager,
                    gameDirectory = engineContext.config.directories.gameDir
                )
                customComponent { scene, deltaSeconds ->
                    if(transform.position.x > 100f) {
                        transform.translation(0f, 0f, 0f)
                    }
                    transform.translate(Vector3f(deltaSeconds * 20f, 0f, 0f))
                }
            }

            entity("Probe0") {
                addComponent(ReflectionProbe(Vector3f(100f), this))
            }
            entity("Probe1") {
                addComponent(ReflectionProbe(Vector3f(200f), this))
            }
        }
    }