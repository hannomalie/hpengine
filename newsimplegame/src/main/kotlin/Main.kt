import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.CustomComponent.Companion.customComponent
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.config
import de.hanno.hpengine.engine.directory.EngineDirectory
import de.hanno.hpengine.engine.directory.GameDirectory
import de.hanno.hpengine.engine.programManager
import de.hanno.hpengine.engine.retrieveConfig
import de.hanno.hpengine.engine.scene.scene
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import java.io.File

fun main(args: Array<String>) {

    val config = retrieveConfig(args,
        gameDirectory = GameDirectory(File("/home/tenter/workspace/hpengine/newsimplegame/src/main/resources/game"))
    )
    val engine = Engine(config)
    val program = engine.programManager.getProgram(FileBasedCodeSource(engine.config.gameDir.resolve("shaders/occlusion_culling1_vertex.glsl")))

    engine.scene = scene("Foo", engine.engineContext) {
        entities {
            entity("Bar") {
                customComponent { scene, deltaSeconds ->
                    println("YYYY")
                }
                val fileName = "doom3monster/monster.md5mesh"
                modelComponent(
                        name = "BarModel",
                        file = fileName,
                        materialManager = scene.materialManager,
                        gameDirectory = engine.config.directories.gameDir
                )
            }
        }
    }
}