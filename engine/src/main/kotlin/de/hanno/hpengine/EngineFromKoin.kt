import com.artemis.BaseSystem
import de.hanno.hpengine.Engine
import de.hanno.hpengine.apiModule
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.directory.Directories
import de.hanno.hpengine.directory.EngineDirectory
import de.hanno.hpengine.directory.GameDirectory
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.opengl.openglModule
import de.hanno.hpengine.scene.AddResourceContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ksp.generated.defaultModule
import java.io.File

operator fun Engine.Companion.invoke(modules: List<Module>): Engine {
    val application = startKoin {
        modules(apiModule, defaultModule)
        modules(modules)
    }

    val koin = application.koin

    return Engine(
        baseSystems = koin.getAll<BaseSystem>(),
        config = koin.get<Config>(),
        input = koin.get<Input>(),
        window = koin.get<Window>(),
        addResourceContext = koin.get<AddResourceContext>()
    )
}

operator fun Engine.Companion.invoke(): Engine {
    val config = Config()

    return Engine(listOf(
        openglModule,
        glfwModule,
        module {
            single { config }
            single { config.gameDir }
            single { config.engineDir }
        }
    ))
}
