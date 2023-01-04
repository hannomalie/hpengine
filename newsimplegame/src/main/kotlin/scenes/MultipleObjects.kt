package scenes

import de.hanno.hpengine.Engine
import de.hanno.hpengine.addAnimatedModelEntity
import de.hanno.hpengine.addStaticModelEntity
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.directory.Directories
import de.hanno.hpengine.directory.EngineDirectory
import de.hanno.hpengine.directory.GameDirectory
import de.hanno.hpengine.extension.deferredRendererModule
import de.hanno.hpengine.extension.simpleForwardRendererModule
import de.hanno.hpengine.graphics.editor.editorModule
import de.hanno.hpengine.loadScene
import de.hanno.hpengine.transform.AABBData
import org.joml.Vector3f
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

    Engine(config, listOf(
        deferredRendererModule,
//        simpleForwardRendererModule,
        editorModule,
    )) {
        world.loadScene {
            addStaticModelEntity("Sponza", "assets/models/sponza.obj")
            addStaticModelEntity("Ferrari", "assets/models/ferrari.obj", translation = Vector3f(100f, 10f, 0f))
            addAnimatedModelEntity("Hellknight",
                "assets/models/doom3monster/monster.md5mesh",
                AABBData(
                    Vector3f(-60f, -10f, -35f),
                    Vector3f(60f, 130f, 50f)
                )
            )
            addAnimatedModelEntity("Bob",
                "assets/models/bob_lamp_update/bob_lamp_update_export.md5mesh",
                AABBData( // This is not accurate, but big enough to not cause culling problems
                    Vector3f(-60f, -10f, -35f),
                    Vector3f(60f, 130f, 50f)
                )
            )
        }
    }
}

