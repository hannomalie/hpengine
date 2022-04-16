package scenes

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.dsl.Directory
import de.hanno.hpengine.engine.scene.dsl.OceanWaterDescription
import de.hanno.hpengine.engine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.engine.scene.dsl.entity
import de.hanno.hpengine.engine.transform.AABBData
import org.joml.Vector3f

val Engine.oceanDemo
    get() = de.hanno.hpengine.engine.scene.dsl.scene("OceanWaterDemo") {
        entity("OceanWater") {
            transform = transform.scaling(10f)
            add(
                StaticModelComponentDescription(
                    file = "assets/models/plane.obj",
                    Directory.Game,
                    AABBData(
                        Vector3f(-60f, -10f, -35f),
                        Vector3f(60f, 130f, 50f)
                    ),
                    material = Material(
                        "ocean",
                        programDescription = application.koin.get<ProgramManager<*>>().heightMappingFirstPassProgramDescription,
                        diffuse = Vector3f(0f, 0f, 1f),
                        metallic = 0.8f,
                        roughness = 0.7f,
                        parallaxScale = 0.3f,
                        parallaxBias = 0.3f,
                    )
                )
            )
            add(OceanWaterDescription())
        }

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