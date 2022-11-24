package scenes

import de.hanno.hpengine.artemis.InstanceComponent
import de.hanno.hpengine.artemis.NameComponent
import de.hanno.hpengine.artemis.TransformComponent
import de.hanno.hpengine.Engine
import de.hanno.hpengine.addStaticModelEntity
import de.hanno.hpengine.artemis.MaterialComponent
import de.hanno.hpengine.config.ConfigImpl
import de.hanno.hpengine.directory.Directories
import de.hanno.hpengine.directory.EngineDirectory
import de.hanno.hpengine.directory.GameDirectory
import de.hanno.hpengine.loadScene
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.scene.dsl.Directory
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

    Engine(config, useEditor = true) {
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
            val times = 100
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
