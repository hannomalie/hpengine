package scenes

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.addAnimatedModelEntity
import de.hanno.hpengine.engine.addStaticModelEntity
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.directory.EngineDirectory
import de.hanno.hpengine.engine.directory.GameDirectory
import de.hanno.hpengine.engine.loadScene
import de.hanno.hpengine.engine.scene.dsl.AnimatedModelComponentDescription
import de.hanno.hpengine.engine.scene.dsl.Directory
import de.hanno.hpengine.engine.scene.dsl.entity
import de.hanno.hpengine.engine.transform.AABBData
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