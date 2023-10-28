package scenes

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceSource
import de.hanno.hpengine.Engine
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.directory.Directories
import de.hanno.hpengine.directory.EngineDirectory
import de.hanno.hpengine.directory.GameDirectory
import de.hanno.hpengine.graphics.editor.editorModule
import de.hanno.hpengine.graphics.renderer.deferred.deferredRendererModule
import de.hanno.hpengine.graphics.renderer.forward.simpleForwardRendererModule
import de.hanno.hpengine.ocean.oceanModule
import de.hanno.hpengine.opengl.openglModule
import glfwModule
import invoke
import org.koin.dsl.module
import java.io.File

fun main() {
    val demoConfig = ConfigLoaderBuilder.default()
        .addEnvironmentSource()
        .addResourceSource("/application.properties")
        .build().loadConfigOrThrow<DemoConfig>()

    val gameDirectory = if(demoConfig.gameDir != null) GameDirectory(demoConfig.gameDir, null) else GameDirectory(File("demo"), Demo::class.java)

    val directories = if(demoConfig.engineDir != null) Directories(
        EngineDirectory(demoConfig.engineDir),
        gameDirectory
    ) else Directories(gameDir = gameDirectory)

    val config = Config(directories = directories)

    val primaryRendererModule = when (demoConfig.renderer) {
        Renderer.Deferred -> deferredRendererModule
        Renderer.Forward -> simpleForwardRendererModule
    }

    val engine = Engine(
        listOf(
            glfwModule,
            openglModule,
            oceanModule,
            primaryRendererModule,
            editorModule,
            module {
                single { config }
                single { config.gameDir }
                single { config.engineDir }
            }
        )
    )

    demoConfig.demo.run(config, engine)
}

enum class Demo(val run: (Config, Engine) -> Unit) {
    LotsOfCubes({ _, _ -> }), // TODO: Make this possible by reimplementing the demo
    MultipleObjects(::runMultipleObjects),
    Ocean(::runOcean),
    Sponza(::runSponza),
}
enum class Renderer {
    Deferred,
    Forward,
}
data class DemoConfig(
    val engineDir: File?,
    val gameDir: File?,
    val demo: Demo = Demo.Ocean,
    val renderer: Renderer = Renderer.Deferred
)
