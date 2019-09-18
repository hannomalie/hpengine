package de.hanno.hpengine.engine

import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.EngineContextImpl
import de.hanno.hpengine.engine.backend.ManagerContext
import de.hanno.hpengine.engine.backend.ManagerContextImpl
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.ScriptComponentFileLoader
import de.hanno.hpengine.engine.config.SimpleConfig
import de.hanno.hpengine.engine.config.populateConfigurationWithProperties
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.event.EngineInitializedEvent
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.SimpleProvider
import de.hanno.hpengine.engine.graphics.renderer.DeferredRenderer
import de.hanno.hpengine.engine.graphics.renderer.Renderer
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.scene.SceneManager
import de.hanno.hpengine.engine.threads.UpdateThread
import de.hanno.hpengine.util.fps.FPSCounter
import de.hanno.hpengine.util.gui.Editor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.logging.Logger
import kotlin.coroutines.CoroutineContext

interface Engine<TYPE: BackendType>: ManagerContext<TYPE> {
    val managerContext: ManagerContext<TYPE>
    val sceneManager: SceneManager

    val scene
        get() = sceneManager.scene
}

class EngineImpl @JvmOverloads constructor(override val engineContext: EngineContext<OpenGl>,
                                           val materialManager: MaterialManager = MaterialManager(engineContext),
                                           val renderer: Renderer<OpenGl> = DeferredRenderer(materialManager, engineContext),
                                           override val renderManager: RenderManager = RenderManager(engineContext, engineContext.renderStateManager, renderer, materialManager),
                                           override val managerContext: ManagerContext<OpenGl> = ManagerContextImpl(engineContext = engineContext, renderManager = renderManager)) : ManagerContext<OpenGl> by managerContext, Engine<OpenGl> {

    private val updateScope = Executors.newFixedThreadPool(8).asCoroutineDispatcher()
    val updateConsumer = Consumer<Float> {
        with(this@EngineImpl) {
            runBlocking(updateScope) { update(it) }
        }
    }
    val updateThread: UpdateThread = UpdateThread(engineContext, updateConsumer, "Update", TimeUnit.MILLISECONDS.toSeconds(8).toFloat())

    override val sceneManager = managerContext.managers.register(SceneManager(managerContext))

    val inputUpdater = SimpleProvider(Runnable {
        input.update()
    })

    init {
        engineContext.eventBus.register(this)
        gpuContext.registerPerFrameCommand(inputUpdater)
        startSimulation()
        engineContext.eventBus.post(EngineInitializedEvent())
    }

    fun startSimulation() {
        updateThread.start()
    }

    fun CoroutineScope.update(deltaSeconds: Float) {
        try {
            engineContext.commandQueue.executeCommands()
            inputUpdater.setReadyForExecution()
            while(inputUpdater.isReadyForExecution()){ }
            with(managerContext.managers) {
                update(deltaSeconds)
            }
            updateRenderState()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateRenderState() {
        val scene = sceneManager.scene

        with(renderManager) {
            if (renderState.currentWriteState.gpuCommandSync.isSignaled) {
                renderState.currentWriteState.cycle = drawCycle.get()
                renderState.currentWriteState.prepareExtraction()
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

            var gameDir = Directories.GAMEDIR_NAME
            var width = 1280
            var height = 720

            var debug = true
            for (string in args) {
                when {
                    "debug=false" == string -> debug = false
                    string.startsWith("gameDir=", true) -> gameDir = string.replace("gameDir=", "", true)
                    "fullhd" == string -> { // TODO: Remove this possibility to set resolution
                        width = 1920
                        height = 1080
                    }
                }
            }

            val config = SimpleConfig()
            config.populateConfigurationWithProperties(File(gameDir))
            config.gameDir = gameDir
            config.width = width
            config.height = height

            val engineContext = EngineContextImpl(config = config)
            val materialManager = MaterialManager(engineContext)
            val renderer: Renderer<OpenGl> = getRendererForPlatform(engineContext, materialManager)
            println("Using renderer class ${renderer.javaClass.simpleName}")
            val engine = EngineImpl(
                    engineContext = engineContext,
                    materialManager = materialManager,
                    renderer = renderer
            )
            if (debug) {
                Editor(engine, config)
            }

            val initScriptFile = engineContext.config.directories.gameDir.initScript
            initScriptFile?.let {
                try {
                    ScriptComponentFileLoader.getLoaderForFileExtension(it.extension).load(engine, it, Entity())
                    println("InitScript initialized")
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

        }

        private fun getRendererForPlatform(
                engineContext: EngineContext<OpenGl>,
                materialManager: MaterialManager
        ): Renderer<OpenGl> {
            return DeferredRenderer(materialManager, engineContext)
//            return when {
//                engineContext.backend.gpuContext.isSupported(BindlessTextures, DrawParameters, Shader5) == Supported -> {
//                    DeferredRenderer(materialManager, engineContext)
//                }
//                else -> SimpleColorRenderer(engineContext, engineContext.programManager, engineContext.textureManager)
//            }
        }
    }
}

