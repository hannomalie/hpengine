package scenes

import de.hanno.hpengine.Engine
import de.hanno.hpengine.addAnimatedModelEntity
import de.hanno.hpengine.config.ConfigImpl
import de.hanno.hpengine.directory.Directories
import de.hanno.hpengine.directory.EngineDirectory
import de.hanno.hpengine.directory.GameDirectory
import de.hanno.hpengine.loadScene
import de.hanno.hpengine.transform.AABBData
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
            addAnimatedModelEntity("Hellknight",
                "assets/models/doom3monster/monster.md5mesh",
                AABBData(
                    Vector3f(-60f, -10f, -35f),
                    Vector3f(60f, 130f, 50f)
                )
            )
        }
    }
}