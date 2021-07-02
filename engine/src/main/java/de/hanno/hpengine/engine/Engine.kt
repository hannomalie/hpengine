package de.hanno.hpengine.engine

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.eventBus
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.input
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.directory.EngineAsset
import de.hanno.hpengine.engine.directory.EngineDirectory
import de.hanno.hpengine.engine.directory.GameAsset
import de.hanno.hpengine.engine.directory.GameDirectory
import de.hanno.hpengine.engine.event.EngineInitializedEvent
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.SceneManager
import de.hanno.hpengine.engine.scene.baseModule
import de.hanno.hpengine.util.fps.FPSCounter
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.receiveOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class Engine @JvmOverloads constructor(
    val engineContext: EngineContext,
    val renderManager: RenderManager,
    val sceneManager: SceneManager
) {

    val cpsCounter = FPSCounter()
    private var updateThreadCounter = 0
    private val updateThreadNamer: (Runnable) -> Thread = { Thread(it).apply { name = "UpdateThread${updateThreadCounter++}" } }
    private val updateScopeDispatcher = Executors.newFixedThreadPool(8, updateThreadNamer).asCoroutineDispatcher()

    var scene: Scene
        get() = sceneManager.scene
        set(value) {
            sceneManager.scene = value
        }

    val directories: Directories = engineContext.config.directories

    val updateCycle = AtomicLong()

    init {
        sceneManager.scene = Scene("InitialScene")
        engineContext.eventBus.register(this)
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
        engineContext.eventBus.post(EngineInitializedEvent())

    }


//    private suspend fun executeCommands() = withContext(engineContext.addResourceContext.singleThreadDispatcher) {
    private suspend fun executeCommands() {
        while (!engineContext.addResourceContext.channel.isEmpty) {
            val command = engineContext.addResourceContext.channel.receiveOrNull() ?: break
            command.invoke()
        }
    }

    private fun extract(deltaSeconds: Float): Long {
        renderManager.renderState.currentWriteState.cycle = updateCycle.get()
        renderManager.renderState.currentWriteState.time = System.currentTimeMillis()

        renderSystems.forEach { it.extract(scene, renderManager.renderState.currentWriteState) }

        renderManager.finishCycle(sceneManager.scene, deltaSeconds)
        return updateCycle.getAndIncrement()
    }

    suspend fun update(deltaSeconds: Float) = try {
        scene.currentCycle = updateCycle.get()

        sceneManager.update(scene, deltaSeconds)

        engineContext.window.invoke { engineContext.input.update() }
        engineContext.window.awaitEvents()
        cpsCounter.update()

    } catch (e: Exception) {
        e.printStackTrace()
    }


    fun EngineAsset(relativePath: String): EngineAsset = config.EngineAsset(relativePath)
    fun GameAsset(relativePath: String): GameAsset = config.GameAsset(relativePath)
    val firstpassProgramVertexSource = EngineAsset("shaders/first_pass_vertex.glsl").toCodeSource()
    val firstpassProgramFragmentSource = EngineAsset("shaders/first_pass_fragment.glsl").toCodeSource()

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
            val application = startKoin {
                modules(configModule, baseModule)
            }

            val engine = application.koin.get<Engine>()
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

inline val Engine.addResourceContext
    get() = engineContext.backend.addResourceContext
inline val Engine.input
    get() = engineContext.backend.input
inline val Engine.textureManager
    get() = engineContext.backend.textureManager
inline val Engine.programManager
    get() = engineContext.backend.programManager
inline val Engine.renderSystems
    get() = engineContext.renderSystems
inline val Engine.gpuContext
    get() = engineContext.gpuContext
inline val Engine.eventBus
    get() = engineContext.backend.eventBus
inline val Engine.window
    get() = engineContext.window
inline val Engine.deferredRenderingBuffer
    get() = engineContext.deferredRenderingBuffer
inline val Engine.config
    get() = engineContext.config