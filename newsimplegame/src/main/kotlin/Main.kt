import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.CustomComponent.Companion.customComponent
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.config
import de.hanno.hpengine.engine.programManager
import de.hanno.hpengine.engine.retrieveConfig
import de.hanno.hpengine.engine.scene.scene
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import java.io.File

fun main(args: Array<String>) {

    val config = retrieveConfig(args)
//        .copy(gameDir = File(Engine.javaClass.classLoader.getResource("game").file))
        .copy(gameDir = File("/home/tenter/workspace/hpengine/newsimplegame/src/main/resources/game"))
        .copy(engineDir = File("/home/tenter/workspace/hpengine/hp"))
    val engine = Engine(config)
    val program = engine.programManager.getProgram(FileBasedCodeSource(engine.config.gameDir.resolve("shaders/occlusion_culling1_vertex.glsl")))

    engine.scene = scene("Foo", engine.engineContext) {
        entities {
            entity("Bar") {
                customComponent { scene, deltaSeconds ->
                    println("YYYY")
                }
                val fileName = "doom3monster/monster.md5mesh"
                val file = engine.config.gameDir.resolve(fileName)
                modelComponent(
                        name = "BarModel",
                        file = file,
                        materialManager = scene.materialManager,
                        gameDirectory = engine.config.directories.gameDir
                )
            }
        }
    }
}