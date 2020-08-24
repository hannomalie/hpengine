package de.hanno.hpengine.engine

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.ManagerContext
import de.hanno.hpengine.engine.backend.addResourceContext
import de.hanno.hpengine.engine.backend.eventBus
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.input
import de.hanno.hpengine.engine.component.ScriptComponentFileLoader
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.config.populateConfigurationWithProperties
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.event.EngineInitializedEvent
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.renderer.ExtensibleDeferredRenderer
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.manager.ManagerRegistry
import de.hanno.hpengine.engine.physics.PhysicsManager
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.SceneManager
import de.hanno.hpengine.util.fps.FPSCounter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.min

class Engine @JvmOverloads constructor(val engineContext: EngineContext,
                                       val renderer: RenderSystem,
                                       val renderManager: RenderManager = RenderManager(engineContext),
                                       val managerContext: ManagerContext = ManagerContext(engineContext = engineContext, renderManager = renderManager)) {

    val cpsCounter = FPSCounter()
    private var updateThreadCounter = 0
    private val updateThreadNamer: (Runnable) -> Thread = { Thread(it).apply { name = "UpdateThread${updateThreadCounter++}" } }
    private val updateScope = Executors.newFixedThreadPool(8, updateThreadNamer).asCoroutineDispatcher()
    init {
        engineContext.renderSystems.add(0, renderer)
    }
    val sceneManager = managerContext.managers.register(SceneManager(managerContext))
    var scene: Scene
        get() = sceneManager.scene
        set(value) {
            managerContext.beforeSetScene(value)

            managerContext.addResourceContext.locked {
                sceneManager.scene = value
            }
            managerContext.afterSetScene()
        }
    val managers: ManagerRegistry = managerContext.managers
    val directories: Directories = engineContext.config.directories
    val physicsManager: PhysicsManager = managerContext.physicsManager

    init {
        engineContext.eventBus.register(this)
        startSimulation()
        engineContext.eventBus.post(EngineInitializedEvent())
    }

    fun startSimulation() {
        GlobalScope.launch(updateScope) {

            var currentTimeNs = System.nanoTime()
            val dtS = 1 / 60.0

            while (true) {
                val newTimeNs = System.nanoTime()
                val frameTimeNs = (newTimeNs - currentTimeNs).toDouble()
                var frameTimeS = frameTimeNs / 1000000000.0
                currentTimeNs = newTimeNs
                while (frameTimeS > 0.0) {
                    val deltaTime = min(frameTimeS, dtS)
                    engineContext.addResourceContext.locked {
                        update(deltaTime.toFloat())
                    }
                    frameTimeS -= deltaTime
                    yield()
                }
            }
        }
    }

    fun CoroutineScope.update(deltaSeconds: Float) = try {
        engineContext.window.invoke { engineContext.input.update() }
        engineContext.gpuContext.update(deltaSeconds)
        with(managerContext.managers) {
            update(scene, deltaSeconds)
        }
        cpsCounter.update()
        renderManager.finishCycle(sceneManager.scene)
    } catch (e: Exception) {
        e.printStackTrace()
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {

            val config = retrieveConfig(args)

            val engineContext = EngineContext(config = config)
            val renderer: RenderSystem = ExtensibleDeferredRenderer(engineContext)
            val engine = Engine(
                engineContext = engineContext,
                renderer = renderer
            )

            engine.executeInitScript()
        }

    }
}

fun Array<String>.extractGameDir(): String {
    var gameDir = Directories.GAMEDIR_NAME

    for (string in this) {
        when {
            string.startsWith("gameDir=", true) -> gameDir = string.replace("gameDir=", "", true)
        }
    }
    return gameDir
}

fun retrieveConfig(args: Array<String>): ConfigImpl {
    val gameDir = args.extractGameDir()

    val config = ConfigImpl(gameDir)
    config.populateConfigurationWithProperties(File(gameDir))
    return config
}

fun Engine.executeInitScript() {
    engineContext.config.directories.gameDir.initScript?.let { initScriptFile ->
        ScriptComponentFileLoader.getLoaderForFileExtension(initScriptFile.extension).load(this, initScriptFile, Entity())
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
inline val Engine.materialManager
    get() = engineContext.materialManager
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