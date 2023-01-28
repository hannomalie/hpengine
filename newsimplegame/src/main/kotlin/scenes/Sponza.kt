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

    val engine = Engine(
        config,
        listOf(
            deferredRendererModule,
            editorModule,
        )
    )
    engine.world.loadScene {
        addStaticModelEntity("Sponza", "assets/models/sponza.obj")
    }
    engine.simulate()
}

