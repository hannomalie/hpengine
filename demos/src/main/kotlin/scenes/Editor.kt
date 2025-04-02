package scenes

import com.artemis.BaseSystem
import de.hanno.hpengine.Engine
import de.hanno.hpengine.apiModule
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.RenderManager
import de.hanno.hpengine.graphics.RenderSystemsConfig
import de.hanno.hpengine.graphics.editor.*
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.fps.FPSCounter
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.texture.TextureManagerBaseSystem
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.opengl.openglModule
import de.hanno.hpengine.scene.AddResourceContext
import de.hanno.hpengine.transform.EntityMovementSystem
import glfwModule
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.ksp.generated.defaultModule

fun main() {

    val demoAndEngineConfig = createDemoAndEngineConfig(Demo.Editor, emptyList<Module>())

    val koin = getKoin(demoAndEngineConfig)
    val baseSystems = koin.getAll<BaseSystem>()
    val engine = Engine(
        baseSystems = baseSystems,
        config = koin.get<Config>(),
        input = koin.get<Input>(),
        window = koin.get<Window>(),
        addResourceContext = koin.get<AddResourceContext>()
    )

    addEditor(koin, engine)

    engine.runSponza()
}

fun getKoin(demoAndEngineConfig: DemoAndEngineConfig): Koin {
    val modules = listOf(
        glfwModule,
        openglModule,
        demoAndEngineConfig.configModule,
    ) + demoAndEngineConfig.demoConfig.demo.additionalModules +
            demoAndEngineConfig.demoConfig.renderer.additionalModules +
            demoAndEngineConfig.additionalModules
    val application = startKoin {
        modules(apiModule, defaultModule)
        modules(modules)
    }

    val koin = application.koin
    return koin
}

fun addEditor(koin: Koin, engine: Engine) {
    val renderStateContext = koin.get<RenderStateContext>()
    val renderManager = koin.get<RenderManager>()
    val editor = ImGuiEditor(
        graphicsApi = koin.get<GraphicsApi>(),
        window = koin.get<Window>(),
        textureManager = koin.get<TextureManagerBaseSystem>(),
        config = koin.get<Config>(),
        addResourceContext = koin.get<AddResourceContext>(),
        fpsCounter = koin.get<FPSCounter>(),
        editorExtensions = koin.getAll<ImGuiEditorExtension>(),
        entityClickListener = EntityClickListener(),//koin.get<EntityClickListener>(),
        primaryCameraStateHolder = koin.get<PrimaryCameraStateHolder>(),
        gpuProfiler = koin.get<GPUProfiler>(),
        renderManager = renderManager,
        programManager = koin.get<ProgramManager>(),
        input = EditorInput(koin.get()),//koin.get<EditorInput>(),
        primaryRendererSelection = PrimaryRendererSelection(koin.get()),//koin.get<PrimaryRendererSelection>(),
        outputSelection = OutputSelection(koin.get(),koin.get(),koin.get(),koin.get(),koin.get(),koin.get()),//koin.get<OutputSelection>(),
        entityMovementSystem = koin.get<EntityMovementSystem>(),
        _editorExtensions = koin.getAll<EditorExtension>(),
    ).apply {
        artemisWorld = engine.world
    }

    val oldPresent = renderManager.present
    renderManager.present = {
        oldPresent()
        editor.render(renderStateContext.renderState.currentReadState)
    }
}

internal fun Engine.runEditor() {
    runMultipleObjects()
}