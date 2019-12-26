package de.hanno.hpengine.engine

import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.EngineContextImpl
import de.hanno.hpengine.engine.backend.ManagerContext
import de.hanno.hpengine.engine.backend.ManagerContextImpl
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.ScriptComponentFileLoader
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.config.populateConfigurationWithProperties
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.event.EngineInitializedEvent
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.renderer.ExtensibleDeferredRenderer
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.SceneManager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.threads.UpdateThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

interface Engine<TYPE : BackendType> : ManagerContext<TYPE> {
    val managerContext: ManagerContext<TYPE>
    val sceneManager: SceneManager

    var scene: Scene
        get() = sceneManager.scene
        set(value) {
            managerContext.beforeSetScene(value)

            managerContext.addResourceContext.locked {
                sceneManager.scene = value
            }
            managerContext.afterSetScene()
        }
    override val addResourceContext: AddResourceContext
        get() = managerContext.addResourceContext
}

class EngineImpl @JvmOverloads constructor(override val engineContext: EngineContext<OpenGl>,
                                           val renderer: RenderSystem,
                                           override val renderManager: RenderManager = RenderManager(engineContext),
                                           override val managerContext: ManagerContext<OpenGl> = ManagerContextImpl(engineContext = engineContext, renderManager = renderManager)) : ManagerContext<OpenGl> by managerContext, Engine<OpenGl> {

    private var updateThreadCounter = 0
    private val function: (Runnable) -> Thread = { Thread(it).apply { name = "UpdateThread${updateThreadCounter++}" } }
    private val updateScope = Executors.newFixedThreadPool(8, function).asCoroutineDispatcher()
    val updateConsumer = Consumer<Float> {
        addResourceContext.locked {
            val job = GlobalScope.launch(updateScope) {
                    update(it)
            }
            while(!job.isCompleted) {}
        }
        Thread.sleep(1)
    }
    val updateThread: UpdateThread = UpdateThread(engineContext, updateConsumer, "Update", TimeUnit.MILLISECONDS.toSeconds(8).toFloat())

    override val sceneManager = managerContext.managers.register(SceneManager(managerContext))

    init {
        engineContext.eventBus.register(this)
        startSimulation()
        engineContext.eventBus.post(EngineInitializedEvent())
        engineContext.renderSystems.add(0, renderer)
    }

    fun startSimulation() {
        updateThread.start()
    }

    fun CoroutineScope.update(deltaSeconds: Float) = try {
        gpuContext.execute("updateInput") { input.update() }
        gpuContext.update(deltaSeconds)
        with(managerContext.managers) {
            update(deltaSeconds)
        }
        updateRenderState()
    } catch (e: Exception) {
        e.printStackTrace()
    }

    private fun updateRenderState() {
        val scene = sceneManager.scene

        with(renderManager) {
            if (renderState.currentWriteState.gpuCommandSync.isSignaled) {
                renderState.currentWriteState.cycle = drawCycle.get()
                renderState.currentWriteState.deltaInS = renderManager.getDeltaInS().toFloat()
                renderManager.extract(renderState.currentWriteState)
                scene.extract(renderState.currentWriteState)
                renderState.update()
            }
            drawCycle.getAndIncrement()
        }
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {

            val config = retrieveConfig(args)

            val engineContext = EngineContextImpl(config = config)
            val renderer: RenderSystem = ExtensibleDeferredRenderer(engineContext)
            val engine = EngineImpl(
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

fun EngineImpl.executeInitScript() {
    config.directories.gameDir.initScript?.let { initScriptFile ->
        ScriptComponentFileLoader.getLoaderForFileExtension(initScriptFile.extension).load(this, initScriptFile, Entity())
    }
}