package de.hanno.hpengine.engine

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.addResourceContext
import de.hanno.hpengine.engine.backend.eventBus
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.input
import de.hanno.hpengine.engine.component.CustomComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.directory.EngineDirectory
import de.hanno.hpengine.engine.directory.GameDirectory
import de.hanno.hpengine.engine.event.EngineInitializedEvent
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.renderer.ExtensibleDeferredRenderer
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.SceneManager
import de.hanno.hpengine.engine.scene.scene
import de.hanno.hpengine.util.fps.FPSCounter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class Engine @JvmOverloads constructor(val engineContext: EngineContext,
                                       val renderer: RenderSystem = ExtensibleDeferredRenderer(engineContext),
                                       val renderManager: RenderManager = RenderManager(engineContext)) {

    constructor(config: Config): this(EngineContext(config))
    constructor() : this(
        ConfigImpl(
            directories = Directories(EngineDirectory(File(Directories.ENGINEDIR_NAME)), GameDirectory(File(Directories.GAMEDIR_NAME), null))
        )
    )

    val cpsCounter = FPSCounter()
    private var updateThreadCounter = 0
    private val updateThreadNamer: (Runnable) -> Thread = { Thread(it).apply { name = "UpdateThread${updateThreadCounter++}" } }
    private val updateScope = Executors.newFixedThreadPool(8, updateThreadNamer).asCoroutineDispatcher()
    val sceneManager = SceneManager(engineContext, Scene("InitialScene", engineContext))

    init {
        sceneManager.scene.extensions.forEach { it.init(sceneManager) }
        engineContext.renderSystems.add(0, renderer)
    }
    var scene: Scene
        get() = sceneManager.scene
        set(value) { sceneManager.scene = value }

    val directories: Directories = engineContext.config.directories

    val updateCycle = AtomicLong()

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
                        val deltaSeconds = deltaTime.toFloat()
                        update(deltaSeconds)
                    }
                    frameTimeS -= deltaTime
                    yield()
                }
            }
        }
    }

    fun CoroutineScope.update(deltaSeconds: Float) = try {
        scene.currentCycle = updateCycle.get()
        scene.run { update(scene, deltaSeconds) }
        renderManager.run { update(scene, deltaSeconds) }

        engineContext.window.invoke { engineContext.input.update() }
        engineContext.update(deltaSeconds)
        cpsCounter.update()
        engineContext.extract(sceneManager.scene, renderManager.renderState.currentWriteState)

        renderManager.renderState.currentWriteState.cycle = updateCycle.get()
        renderManager.renderState.currentWriteState.time = System.currentTimeMillis()

        renderManager.finishCycle(sceneManager.scene)

        updateCycle.getAndIncrement()
    } catch (e: Exception) {
        e.printStackTrace()
    }


    companion object {

        @JvmStatic
        fun main(args: Array<String>) {

            val engine = Engine();

            // This can be run as a default test scene
            {
                engine.scene = scene("Foo", engine.engineContext) {
                    entities {
                        entity("Bar") {
                            val entity = this
                            addComponent(object : CustomComponent {
                                override val entity = entity
                                override fun CoroutineScope.update(scene: Scene, deltaSeconds: Float) = println("XXXXXXXXXXXXXXXXXXXX")
                            })
                        }
                    }
                }
            }
        }

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