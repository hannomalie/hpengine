package scenes

import de.hanno.hpengine.Engine
import de.hanno.hpengine.addStaticModelEntity
import de.hanno.hpengine.config.ConfigImpl
import de.hanno.hpengine.directory.Directories
import de.hanno.hpengine.directory.EngineDirectory
import de.hanno.hpengine.directory.GameDirectory
import de.hanno.hpengine.loadScene
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
            addStaticModelEntity("Sponza", "assets/models/sponza.obj")
        }
    }
}

