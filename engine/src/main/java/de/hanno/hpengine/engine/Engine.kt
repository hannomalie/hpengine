package de.hanno.hpengine.engine

import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.directory.EngineDirectory
import de.hanno.hpengine.engine.directory.GameDirectory
import de.hanno.hpengine.engine.extension.Extension
import de.hanno.hpengine.engine.extension.baseModule
import de.hanno.hpengine.engine.extension.deferredRendererModule
import de.hanno.hpengine.engine.graphics.GlfwWindow
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.OpenGLContext
import de.hanno.hpengine.engine.graphics.OpenGlExecutorImpl
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.Window
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.SceneManager
import de.hanno.hpengine.engine.scene.dsl.SceneDescription
import de.hanno.hpengine.engine.scene.dsl.convert
import de.hanno.hpengine.util.fps.CPSCounter
import de.hanno.hpengine.util.fps.FPSCounter
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.receiveOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
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

        renderManager.finishCycle(sceneManager.scene, deltaSeconds)

        return updateCycle.getAndIncrement()
    }

    suspend fun update(deltaSeconds: Float) = try {
        scene.currentCycle = updateCycle.get()

        koin.getAll<Extension>().distinct().forEach { it.update(scene, deltaSeconds) }
        sceneManager.update(scene, deltaSeconds)

        window.invoke { input.update() }
        window.awaitEvents()

    } catch (e: Exception) {
        e.printStackTrace()
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {

            val configModule = module {
                single<Config> {
                    ConfigImpl(
                        directories = Directories(
                            EngineDirectory(File(Directories.ENGINEDIR_NAME)),
                            GameDirectory(File(Directories.GAMEDIR_NAME), null)
                        )
                    )
                }
            }
            val windowModule = module {
                single { GlfwWindow(get()) } bind Window::class
            }
            val application = startKoin {
                modules(configModule, windowModule, baseModule, deferredRendererModule)
            }

            val engine = Engine(application)
        }

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

            frameTimeS -= deltaTime
            yield()
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
