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
//            val bpcem = addStaticModelEntity("BPCEM", "assets/models/bpcem_playground.obj", Directory.Game)
            val cube = addStaticModelEntity("Cube", "assets/models/cube.obj", Directory.Engine)

            edit(create()).apply {
                create(TransformComponent::class.java).apply {
                    transform.transformation.translation(Vector3f(20f, 0f, 0f))
                }
                create(NameComponent::class.java).apply {
                    this.name = "Instance1"
                }
                create(InstanceComponent::class.java).apply {
                    targetEntity = cube.entityId
                }
            }
            edit(create()).apply {
                create(TransformComponent::class.java).apply {
                    transform.transformation.translation(Vector3f(20f, 0f, 20f))
                }
                create(NameComponent::class.java).apply {
                    this.name = "Instance2"
                }
                create(InstanceComponent::class.java).apply {
                    targetEntity = cube.entityId
                }
                create(MaterialComponent::class.java).apply {
                    material = Material("green").apply {
                        diffuse.x = 0.0f
                        diffuse.y = 1.0f
                        diffuse.z = 0.0f
                    }
                }
            }
        }
    }
}