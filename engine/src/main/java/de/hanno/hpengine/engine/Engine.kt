package de.hanno.hpengine.engine

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.World
import com.artemis.WorldConfigurationBuilder
import com.artemis.injection.WiredFieldResolver
import com.artemis.managers.TagManager
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy
import de.hanno.hpengine.engine.component.artemis.*
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.config.DebugConfig
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.directory.EngineDirectory
import de.hanno.hpengine.engine.directory.GameDirectory
import de.hanno.hpengine.engine.extension.Extension
import de.hanno.hpengine.engine.extension.baseModule
import de.hanno.hpengine.engine.extension.deferredRendererModule
import de.hanno.hpengine.engine.extension.imGuiEditorModule
import de.hanno.hpengine.engine.graphics.GlfwWindow
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.Window
import de.hanno.hpengine.engine.graphics.imgui.EditorCameraInputSystemNew
import de.hanno.hpengine.engine.graphics.imgui.ImGuiEditor
import de.hanno.hpengine.engine.graphics.imgui.primaryCamera
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.model.EntityBuffer
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.SceneManager
import de.hanno.hpengine.engine.scene.dsl.*
import de.hanno.hpengine.engine.transform.TransformSpatial
import io.github.config4k.registerCustomType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.receiveOrNull
import net.mostlyoriginal.api.SingletonPlugin
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import org.objenesis.strategy.StdInstantiatorStrategy
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min


class Engine constructor(val application: KoinApplication) {

    private val koin = application.koin
    private val config = koin.get<Config>()
    private val addResourceContext = koin.get<AddResourceContext>()
    private val window = koin.get<Window<*>>()
    private val input = koin.get<Input>()
    private val renderManager = koin.get<RenderManager>()
    private val sceneManager = koin.get<SceneManager>()


    val modelSystem = ModelSystem(
        config,
        application.koin.get(),
        application.koin.get(),
        MaterialManager(config, application.koin.get(), application.koin.get()),
        EntityBuffer()
    )
    val componentExtractor = ComponentExtractor()
    val worldConfigurationBuilder = WorldConfigurationBuilder().with(
        TagManager(),
        componentExtractor,
        modelSystem,
        CustomComponentSystem(),
        SpatialComponentSystem(),
        EditorCameraInputSystemNew(),
    ).run {
        with(*(koin.getAll<BaseSystem>().toTypedArray()))
    }.run {
        register(SingletonPlugin.SingletonFieldResolver())
    }
    val world = World(
        worldConfigurationBuilder.build().register(
            Kryo().apply {
                isRegistrationRequired = false
                instantiatorStrategy = DefaultInstantiatorStrategy(StdInstantiatorStrategy())
            }
        ).register(input)
    ).apply {
        renderManager.renderSystems.forEach { it.world = this }

        process()

        edit(create()).apply {
            create(TransformComponent::class.java)
            create(ModelComponent::class.java).apply {
                modelComponentDescription = StaticModelComponentDescription("assets/models/cube.obj", Directory.Engine)
            }
            create(SpatialComponent::class.java)
            create(NameComponent::class.java).apply {
                name = "Cube"
            }
        }

        edit(create()).apply {
            create(TransformComponent::class.java)
            getSystem(TagManager::class.java).register(primaryCamera, entityId)
            create(NameComponent::class.java).apply {
                name = "PrimaryCamera"
            }
        }
    }

    private var updateThreadCounter = 0
    private val updateThreadNamer: (Runnable) -> Thread =
        { Thread(it).apply { name = "UpdateThread${updateThreadCounter++}" } }
    private val updateScopeDispatcher = Executors.newFixedThreadPool(8, updateThreadNamer).asCoroutineDispatcher()

    var scene: Scene
        get() = sceneManager.scene
        set(value) {
            sceneManager.scene = value
        }

    val updateCycle = AtomicLong()

    init {
        sceneManager.scene = SceneDescription("InitialScene").convert(config = koin.get(), textureManager = koin.get())
        launchEndlessLoop { deltaSeconds ->
            try {
                executeCommands()
                withContext(updateScopeDispatcher) {
                    update(deltaSeconds)
                }
                extract(deltaSeconds)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        window.pollEventsInLoop()
    }


    //    private suspend fun executeCommands() = withContext(addResourceContext.singleThreadDispatcher) {
    private suspend fun executeCommands() {
        while (!addResourceContext.channel.isEmpty) {
            val command = addResourceContext.channel.receiveOrNull() ?: break
            command.invoke()
        }
    }

    private fun extract(deltaSeconds: Float): Long {
        val currentWriteState = renderManager.renderState.currentWriteState
        currentWriteState.cycle = updateCycle.get()
        currentWriteState.time = System.currentTimeMillis()

        koin.getAll<RenderSystem>().distinct().forEach { it.extract(scene, currentWriteState) }

        modelSystem.extract(currentWriteState)
        componentExtractor.extract(currentWriteState)

        renderManager.finishCycle(sceneManager.scene, deltaSeconds)

        return updateCycle.getAndIncrement()
    }

    suspend fun update(deltaSeconds: Float) = try {
        scene.currentCycle = updateCycle.get()

        koin.getAll<Extension>().distinct().forEach { it.update(scene, deltaSeconds) }
        sceneManager.update(scene, deltaSeconds)

        window.invoke { input.update() }

    } catch (e: Exception) {
        e.printStackTrace()
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {

            val config = ConfigImpl(
                directories = Directories(
                    EngineDirectory(File("C:\\Users\\Tenter\\workspace\\hpengine\\engine\\src\\main\\resources\\hp")),
                    GameDirectory(File(Directories.GAMEDIR_NAME), null)
                ),
                debug = DebugConfig(isEditorOverlay = true)
            )

            val configModule = module {
                single<Config> { config }
            }
            val windowModule = module {
                single { GlfwWindow(get()) } bind Window::class
            }
            val application = startKoin {
                modules(configModule, windowModule, baseModule, deferredRendererModule, imGuiEditorModule)
            }

            val engine = Engine(application)
        }

    }

    fun launchEndlessLoop(actualUpdateStep: suspend (Float) -> Unit): Job = GlobalScope.launch {
        var currentTimeNs = System.nanoTime()
        val dtS = 1 / 60.0

        while (true) {
            val newTimeNs = System.nanoTime()
            val frameTimeNs = (newTimeNs - currentTimeNs).toDouble()
            var frameTimeS = frameTimeNs / 1000000000.0
            currentTimeNs = newTimeNs
            while (frameTimeS > 0.0) {
                val deltaTime = min(frameTimeS, dtS)
                val deltaSeconds = deltaTime.toFloat()

                actualUpdateStep(deltaSeconds)
                world.delta = deltaTime.toFloat()
                world.process()

                frameTimeS -= deltaTime
                yield()
            }
        }
    }
}


fun launchEndlessRenderLoop(actualUpdateStep: suspend (Float) -> Unit): Job = GlobalScope.launch {
    var currentTimeNs = System.nanoTime()
    val dtS = 1 / 60.0

    while (true) {
        val newTimeNs = System.nanoTime()
        val frameTimeNs = (newTimeNs - currentTimeNs).toDouble()
        val frameTimeS = frameTimeNs / 1000000000.0
        currentTimeNs = newTimeNs
        val deltaTime = frameTimeS//min(frameTimeS, dtS)
        val deltaSeconds = deltaTime.toFloat()

        actualUpdateStep(deltaSeconds)
    }
}
