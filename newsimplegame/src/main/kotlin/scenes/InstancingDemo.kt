package scenes

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.addStaticModelEntity
import de.hanno.hpengine.engine.component.artemis.*
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.directory.EngineDirectory
import de.hanno.hpengine.engine.directory.GameDirectory
import de.hanno.hpengine.engine.loadScene
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.dsl.Directory
import de.hanno.hpengine.engine.scene.dsl.StaticModelComponentDescription
import org.joml.Vector3f
import java.io.File
import kotlin.random.Random

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
            val cube = addStaticModelEntity("Cube", "assets/models/cube.obj", Directory.Engine)
            val sphere = addStaticModelEntity("Sphere", "assets/models/sphere.obj", Directory.Engine, Vector3f(0f, 0f, -20f))

            edit(create()).apply {
                create(TransformComponent::class.java).apply {
                    transform.transformation.translation(Vector3f(-20f, 0f, 0f))
                }
                create(NameComponent::class.java).apply {
                    this.name = "Instance0"
                }
                create(InstanceComponent::class.java).apply {
                    targetEntity = cube.entityId
                }
            }
            val random = Random
            val times = 10
            val timesSquared = times*times
            repeat(times) { x ->
                repeat(times) { z ->
                    val instanceIdentifier = times * x + z + 1
                    edit(create()).apply {
                        create(TransformComponent::class.java).apply {
                            transform.transformation.translation(Vector3f(20f * x, 0f, 20f * z))
                        }
                        create(NameComponent::class.java).apply {
                            this.name = "CubeInstance$instanceIdentifier"
                        }
                        create(InstanceComponent::class.java).apply {
                            targetEntity = cube.entityId
                        }
                        create(MaterialComponent::class.java).apply {
                            material = Material("CubeColor$instanceIdentifier").apply {
                                diffuse.x = random.nextFloat()
                                diffuse.y = random.nextFloat()
                                diffuse.z = random.nextFloat()
                            }
                        }
                    }
                }
            }
            repeat(times) { x ->
                repeat(times) { z ->
                    val instanceIdentifier = times * x + z + 1
                    edit(create()).apply {
                        create(TransformComponent::class.java).apply {
                            transform.transformation.translation(Vector3f(20f * x, 50f, 20f * z))
                        }
                        create(NameComponent::class.java).apply {
                            this.name = "SphereInstance$instanceIdentifier"
                        }
                        create(InstanceComponent::class.java).apply {
                            targetEntity = sphere.entityId
                        }
                        create(MaterialComponent::class.java).apply {
                            material = Material("SphereColor$instanceIdentifier").apply {
                                diffuse.x = random.nextFloat()
                                diffuse.y = random.nextFloat()
                                diffuse.z = random.nextFloat()
                            }
                        }
                    }
                }
            }
        }
    }
}
