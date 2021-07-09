package scenes

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.graphics.renderer.extensions.ReflectionProbe
import de.hanno.hpengine.engine.model.material.MaterialInfo
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.scene.scene
import de.hanno.hpengine.engine.textureManager
import org.joml.Vector3f

val Engine.materialsDemo
    get() = scene("MaterialsDemo") {
        entities {
            entity("PlaneStone") {
                modelComponent(
                    name = "PlaneStone",
                    file = "assets/models/plane.obj",
                    materialManager = engineContext.extensions.materialExtension.manager,
                    gameDirectory = engineContext.config.directories.gameDir
                ).apply {
                    this.material = engineContext.extensions.materialExtension.manager.registerMaterial(
                        "stone",
                        MaterialInfo().apply {
                            put(
                                SimpleMaterial.MAP.DIFFUSE,
                                textureManager.getTexture(
                                    "assets/textures/stone_diffuse.png",
                                    true,
                                    engineContext.gameDir
                                )
                            )
                            put(
                                SimpleMaterial.MAP.NORMAL,
                                textureManager.getTexture(
                                    "assets/textures/stone_normal.png",
                                    directory = engineContext.gameDir
                                )
                            )
                            put(
                                SimpleMaterial.MAP.HEIGHT,
                                textureManager.getTexture(
                                    "assets/textures/stone_height.png",
                                    directory = engineContext.gameDir
                                )
                            )
                            program = engineContext.backend.programManager.heightMappingFirstPassProgram
                            parallaxScale = 0.3f
                            parallaxBias = 0.3f
                        })
                }
                transform.scale(10f)
            }
            entity("PlaneBricks") {
                modelComponent(
                    name = "PlaneBricks",
                    file = "assets/models/plane.obj",
                    materialManager = engineContext.extensions.materialExtension.manager,
                    gameDirectory = engineContext.config.directories.gameDir
                ).apply {
                    this.material = engineContext.extensions.materialExtension.manager.registerMaterial(
                        "bricks",
                        MaterialInfo().apply {
                            put(
                                SimpleMaterial.MAP.DIFFUSE,
                                textureManager.getTexture(
                                    "assets/models/textures/Sponza_Bricks_a_Albedo.png",
                                    true,
                                    engineContext.gameDir
                                )
                            )
                            put(
                                SimpleMaterial.MAP.NORMAL,
                                textureManager.getTexture(
                                    "assets/models/textures/Sponza_Bricks_a_Normal.png",
                                    directory = engineContext.gameDir
                                )
                            )
                            put(
                                SimpleMaterial.MAP.HEIGHT,
                                textureManager.getTexture(
                                    "assets/models/textures/Sponza_Bricks_a_height.dds",
                                    directory = engineContext.gameDir
                                )
                            )
                            program = engineContext.backend.programManager.heightMappingFirstPassProgram
                            parallaxScale = 0.3f
                            parallaxBias = 0.3f
                        })
                }
                transform.translate(Vector3f(10f, 0f, 0f))
                transform.scale(10f)
            }

            entity("Probe0") {
                addComponent(ReflectionProbe(Vector3f(100f), this))
            }
        }
    }