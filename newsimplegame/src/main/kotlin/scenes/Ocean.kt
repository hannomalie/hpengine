package scenes

import com.artemis.World
import de.hanno.hpengine.Engine
import de.hanno.hpengine.model.MaterialComponent
import de.hanno.hpengine.model.ModelComponent
import de.hanno.hpengine.component.NameComponent
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.ocean.OceanWaterComponent
import de.hanno.hpengine.ocean.OceanWaterRenderSystem
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.scene.dsl.Directory
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.spatial.SpatialComponent
import de.hanno.hpengine.world.loadScene
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector3fc

fun main() {
    val demoAndEngineConfig = createDemoAndEngineConfig()

    val engine = createEngine(demoAndEngineConfig)

    engine.runOcean()
}

fun Engine.runOcean() {
    world.loadScene {
        addOceanSurface(
            systems.firstIsInstance<ProgramManager>(),
            systems.firstIsInstance<OceanWaterRenderSystem>(),
            Vector3f()
        )
    }
    simulate()
}

private var oceanSurfaceCounter = 0
private fun World.addOceanSurface(
    programManager: ProgramManager,
    oceanWaterRenderSystem: OceanWaterRenderSystem,
    translation: Vector3fc
) {
    edit(create()).apply {
        create(TransformComponent::class.java).apply {
            transform.scaling(10f)
            transform.translate(translation)
        }
        create(ModelComponent::class.java).apply {
            modelComponentDescription = StaticModelComponentDescription(
                file = "assets/models/plane_tesselated.obj",
                Directory.Engine,
//                AABBData(
//                    Vector3f(-60f, -10f, -35f),
//                    Vector3f(60f, 130f, 50f)
//                ),
            )
        }
        create(MaterialComponent::class.java).apply {
            material = Material(
                "ocean",
                programDescription = programManager.heightMappingFirstPassProgramDescription.copy(
                    fragmentShaderSource = FileBasedCodeSource(programManager.config.engineDir.resolve("shaders/ocean/heightmapping_ocean_fragment.glsl"))
                ),
                diffuse = Vector3f(0.05f, 0.05f, 0.4f),
                metallic = 0.95f,
                roughness = 0.001f,
                parallaxScale = 1f,
                parallaxBias = 0.0f,
                useWorldSpaceXZAsTexCoords = true,
                uvScale = Vector2f(0.05f)
            ).apply {
                maps.putIfAbsent(Material.MAP.DIFFUSE, oceanWaterRenderSystem.albedoMap)
                maps.putIfAbsent(Material.MAP.DISPLACEMENT, oceanWaterRenderSystem.displacementMap)
                maps.putIfAbsent(Material.MAP.NORMAL, oceanWaterRenderSystem.waterNormalMap)
            }
        }
        add(OceanWaterComponent())
        create(SpatialComponent::class.java)
        create(NameComponent::class.java).apply {
            name = "OceanSurface${oceanSurfaceCounter++}"
        }
    }
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