package scenes

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceSource
import de.hanno.hpengine.Engine
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.directory.Directories
import de.hanno.hpengine.directory.EngineDirectory
import de.hanno.hpengine.directory.GameDirectory
import de.hanno.hpengine.graphics.RenderManager
import de.hanno.hpengine.graphics.editor.PrimaryRendererSelection
import de.hanno.hpengine.graphics.editor.editorModule
import de.hanno.hpengine.graphics.renderer.deferred.ExtensibleDeferredRenderer
import de.hanno.hpengine.graphics.renderer.deferred.deferredRendererModule
import de.hanno.hpengine.graphics.renderer.forward.ColorOnlyRenderer
import de.hanno.hpengine.graphics.renderer.forward.NoOpRenderer
import de.hanno.hpengine.graphics.renderer.forward.VisibilityRenderer
import de.hanno.hpengine.graphics.renderer.forward.simpleForwardRendererModule
import de.hanno.hpengine.ocean.oceanModule
import de.hanno.hpengine.opengl.openglModule
import glfwModule
import invoke
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.config.Configurator
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.module
import org.koin.ksp.generated.module
import java.io.File

fun main() {
    val demoAndEngineConfig = createDemoAndEngineConfig()
    Configurator.setAllLevels(LogManager.getRootLogger().name, demoAndEngineConfig.config.logLevel)

    val engine = createEngine(demoAndEngineConfig)

    engine.systems.firstIsInstance<PrimaryRendererSelection>().apply {
        val renderSystems = engine.systems.firstIsInstance<RenderManager>().renderSystemsConfig.renderSystems
        primaryRenderer = when(demoAndEngineConfig.demoConfig.renderer) {
            Renderer.Deferred -> renderSystems.firstIsInstance<ExtensibleDeferredRenderer>()
            Renderer.Forward -> renderSystems.firstIsInstance<ColorOnlyRenderer>()
            Renderer.Visibility -> renderSystems.firstIsInstance<VisibilityRenderer>()
            Renderer.NoOp -> renderSystems.firstIsInstance<NoOpRenderer>()
        }
    }

    demoAndEngineConfig.demoConfig.demo.run(engine)
}

fun createEngine(demoAndEngineConfig: DemoAndEngineConfig) = Engine(
    listOf(
        glfwModule,
        openglModule,
//        deferredRendererModule,
        simpleForwardRendererModule,
        editorModule,
        demoAndEngineConfig.configModule,
        DemoModule().module
    ) + demoAndEngineConfig.demoConfig.demo.additionalModules
)

@Module
@ComponentScan
class DemoModule

fun createDemoAndEngineConfig(): DemoAndEngineConfig {
    val demoConfig = ConfigLoaderBuilder.default()
        .addEnvironmentSource()
        .addResourceSource("/application.properties")
        .build().loadConfigOrThrow<DemoConfig>()

    val gameDirectory = if (demoConfig.gameDir != null) GameDirectory(demoConfig.gameDir, null) else GameDirectory(
        File("game"),
        Demo::class.java
    )

    val directories = if (demoConfig.engineDir != null) Directories(
        EngineDirectory(demoConfig.engineDir),
        gameDirectory
    ) else Directories(gameDir = gameDirectory)

    val config = Config(directories = directories, logLevel = Level.getLevel(demoConfig.logLevel))

    return DemoAndEngineConfig(demoConfig, config)
}

enum class Demo(val run: (Engine) -> Unit, val additionalModules: List<org.koin.core.module.Module> = emptyList()) {
    LotsOfGrass(Engine::runLotsOfGrass),
    MultipleObjects(Engine::runMultipleObjects),
    Ocean(Engine::runOcean, listOf(oceanModule)),
    Sponza(Engine::runSponza),
    CPUParticles(Engine::runCPUParticles),
    GPUParticles(Engine::runGPUParticles),
    SkyBox(Engine::runSkyBox),
}
enum class Renderer {
    Deferred,
    Forward,
    Visibility,
    NoOp
}
data class DemoConfig(
    val engineDir: File?,
    val gameDir: File?,
    val demo: Demo = Demo.Ocean,
    val renderer: Renderer = Renderer.Deferred,
    val logLevel: String = "INFO",
)
data class DemoAndEngineConfig(
    val demoConfig: DemoConfig,
    val config: Config
) {
    val configModule = module {
        single { config }
        single { config.gameDir }
        single { config.engineDir }
    }
}
