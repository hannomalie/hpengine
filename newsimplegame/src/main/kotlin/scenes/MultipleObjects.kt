package scenes

import de.hanno.hpengine.Engine.Companion
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.directory.Directories
import de.hanno.hpengine.directory.EngineDirectory
import de.hanno.hpengine.directory.GameDirectory
import de.hanno.hpengine.graphics.editor.editorModule
import de.hanno.hpengine.graphics.renderer.deferred.deferredRendererModule
import de.hanno.hpengine.opengl.openglModule
import de.hanno.hpengine.transform.AABBData
import de.hanno.hpengine.world.addAnimatedModelEntity
import de.hanno.hpengine.world.addStaticModelEntity
import de.hanno.hpengine.world.loadScene
import glfwModule
import invoke
import org.joml.Vector3f
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.CompletableFuture

fun main() {

    val config = Config(
        directories = Directories(
            EngineDirectory(File("""C:\workspace\hpengine\engine\src\main\resources\hp""")),
            GameDirectory(File("""C:\workspace\hpengine\newsimplegame\src\main\resources\game"""), null)
        ),
    )

    val engine = Companion(
        listOf(
            glfwModule,
            openglModule,
            deferredRendererModule,
            editorModule,
            module {
                single { config }
                single { config.gameDir }
                single { config.engineDir }
            }
        )
    )
    CompletableFuture.supplyAsync {
        Thread.sleep(5000)
        engine.world.loadScene {
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
    engine.simulate()
}

