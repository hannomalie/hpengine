package scenes

import de.hanno.hpengine.Engine
import de.hanno.hpengine.component.NameComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.directory.Directories
import de.hanno.hpengine.directory.EngineDirectory
import de.hanno.hpengine.directory.GameDirectory
import de.hanno.hpengine.kotlin.KotlinComponent
import de.hanno.hpengine.loadScene
import de.hanno.hpengine.ressources.FileBasedCodeSource
import java.io.File

fun main() {

    val config = Config(
        directories = Directories(
//                    EngineDirectory(File("C:\\Users\\Tenter\\workspace\\hpengine\\engine\\src\\main\\resources\\hp")),
            EngineDirectory(File("C:\\workspace\\hpengine\\engine\\src\\main\\resources\\hp")),
//                    GameDirectory(File(Directories.GAMEDIR_NAME), null)
            GameDirectory(File("C:\\workspace\\hpengine\\newsimplegame\\src\\main\\resources\\game"), null)
        ),
    )

    Engine {
        world.loadScene {
            edit(create()).apply {
                create(KotlinComponent::class.java).apply {
                    codeSource = FileBasedCodeSource(config.gameDir.assets.resolve("scripts/EditorExtension.kt"))
                }
                create(NameComponent::class.java).apply {
                    this.name = "EditorExtension"
                }
            }
        }
    }
}

