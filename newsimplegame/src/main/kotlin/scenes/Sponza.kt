package scenes

import de.hanno.hpengine.Engine
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.directory.Directories
import de.hanno.hpengine.directory.EngineDirectory
import de.hanno.hpengine.directory.GameDirectory
import de.hanno.hpengine.graphics.editor.editorModule
import de.hanno.hpengine.graphics.renderer.deferred.deferredRendererModule
import de.hanno.hpengine.graphics.renderer.forward.simpleForwardRendererModule
import de.hanno.hpengine.opengl.openglModule
import de.hanno.hpengine.world.addStaticModelEntity
import de.hanno.hpengine.world.loadScene
import glfwModule
import invoke
import org.koin.dsl.module
import java.io.File

fun main() {

    val config = Config(
        directories = Directories(
            EngineDirectory(File("""D:\workspace\hpengine\engine\src\main\resources\hp""")),
            GameDirectory(File("""D:\workspace\hpengine\newsimplegame\src\main\resources\game"""), null)
        ),
    )

    val engine = Engine(
        listOf(
            glfwModule,
            openglModule,
            deferredRendererModule,
//            simpleForwardRendererModule,
            editorModule,
            module {
                single { config }
                single { config.gameDir }
                single { config.engineDir }
            }
        )
    )
    engine.world.loadScene {
        addStaticModelEntity("Sponza", "assets/models/sponza.obj")
    }
    engine.simulate()
}

