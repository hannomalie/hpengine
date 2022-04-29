package scenes

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.addAnimatedModelEntity
import de.hanno.hpengine.engine.addStaticModelEntity
import de.hanno.hpengine.engine.component.artemis.*
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.directory.EngineDirectory
import de.hanno.hpengine.engine.directory.GameDirectory
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.loadScene
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.dsl.*
import de.hanno.hpengine.engine.transform.AABBData
import org.joml.Vector2f
import org.joml.Vector3f
import java.io.File

fun main() {

    val config = ConfigImpl(
        directories = Directories(
//                    EngineDirectory(File("C:\\Users\\Tenter\\workspace\\hpengine\\engine\\src\\main\\resources\\hp")),
            EngineDirectory(File("C:\\workspace\\hpengine\\engine\\src\\main\\resources\\hp")),
//                    GameDirectory(File(Directories.GAMEDIR_NAME), null)
            GameDirectory(File("C:\\workspace\\hpengine\\newsimplegame\\src\\main\\resources\\game"), null)
        ),
    )

    Engine(config) {
        world.loadScene {
            addStaticModelEntity("Cube", "assets/models/cube.obj", Directory.Engine)
            edit(create()).apply {
                create(TransformComponent::class.java).apply {
                    transform.scaling(10f)
                }
                create(ModelComponent::class.java).apply {
                    modelComponentDescription = StaticModelComponentDescription(
                        file = "assets/models/plane_tesselated.obj",
                        Directory.Engine,
                        AABBData(
                            Vector3f(-60f, -10f, -35f),
                            Vector3f(60f, 130f, 50f)
                        ),
                        material = Material(
                            "ocean",
                            programDescription = application.koin.get<ProgramManager<*>>().heightMappingFirstPassProgramDescription,
                            diffuse = Vector3f(0.05f, 0.05f, 0.4f),
                            metallic = 0.95f,
                            roughness = 0.001f,
                            parallaxScale = 1f,
                            parallaxBias = 0.0f,
                            useWorldSpaceXZAsTexCoords = true,
                            uvScale = Vector2f(0.05f)
                        )
                    )
                }
                create(SpatialComponent::class.java)
                create(NameComponent::class.java).apply {
                    this.name = "OceanWater"
                }
                create(OceanWaterComponent::class.java).apply {
                    windspeed = 130f
                    scaleY = 1.2f
                    timeFactor = 8f
                }
            }
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