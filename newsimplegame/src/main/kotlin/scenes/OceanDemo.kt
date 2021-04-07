package scenes

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.programManager
import de.hanno.hpengine.engine.component.CustomComponent.Companion.customComponent
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.graphics.renderer.extensions.ReflectionProbe
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.model.Instance
import de.hanno.hpengine.engine.model.material.MaterialInfo
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.scene.CameraExtension.Companion.camera
import de.hanno.hpengine.engine.scene.OceanWaterExtension
import de.hanno.hpengine.engine.scene.scene
import de.hanno.hpengine.engine.textureManager
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.engine.transform.TransformSpatial
import org.joml.Vector3f

val Engine.oceanDemo
    get() = scene("OceanWaterDemo") {
        entities {
            entity("OceanWater") {
                modelComponent(
                        name = "OceanWater",
                        file = "assets/models/plane.obj",
                        materialManager = scene.materialManager,
                        modelComponentManager = scene.modelComponentManager,
                        gameDirectory = engineContext.config.directories.gameDir
                ).apply {
                    this.material = scene.materialManager.registerMaterial("ocean", MaterialInfo().apply {
                        program = engineContext.programManager.heightMappingFirstPassProgram
                        diffuse.set(0f,0f,1f)
                        metallic = 0.8f
                        roughness = 0.7f
                        parallaxScale = 0.3f
                        parallaxBias = 0.3f
                    })
                }
                addComponent(OceanWaterExtension.OceanWater(this))
                transform.scaling(10f)

//                This has cracks between the instances
//                addComponent(ClustersComponent(this@entity).apply {
//                    repeat(6) { x ->
//                        repeat(6) { z ->
//                            val instanceTransform = Transform().apply {
//                                scaling(10f)
//                                translateLocal(Vector3f(20f * (x-3), 0f, 20f * (z-3)))
//                            }
//                            addInstances(listOf(Instance(this@entity, spatial = TransformSpatial(instanceTransform, AABB()), transform = instanceTransform)))
//                        }
//                    }
//                })
            }
        }
    }