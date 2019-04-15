package de.hanno.hpengine.engine

import de.hanno.hpengine.engine.DirectoryManager.GAMEDIR_NAME
import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.EngineContextImpl
import de.hanno.hpengine.engine.backend.ManagerContext
import de.hanno.hpengine.engine.backend.ManagerContextImpl
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.JavaComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.EngineInitializedEvent
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.SimpleProvider
import de.hanno.hpengine.engine.graphics.renderer.DeferredRenderer
import de.hanno.hpengine.engine.graphics.renderer.Renderer
import de.hanno.hpengine.engine.graphics.renderer.SimpleLinesRenderer
import de.hanno.hpengine.engine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.scene.SceneManager
import de.hanno.hpengine.engine.threads.UpdateThread
import de.hanno.hpengine.util.fps.FPSCounter
import de.hanno.hpengine.util.gui.DebugFrame
import sun.java2d.pipe.SpanShapeRenderer
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.logging.Logger

interface Engine<TYPE: BackendType>: ManagerContext<TYPE> {
    val managerContext: ManagerContext<TYPE>
    val sceneManager: SceneManager

    val scene
        get() = sceneManager.scene
}

class EngineImpl @JvmOverloads constructor(override val engineContext: EngineContext<OpenGl> = EngineContextImpl(),
                                           val materialManager: MaterialManager = MaterialManager(engineContext),
                                           val renderer: Renderer<OpenGl> = SimpleLinesRenderer(engineContext.programManager),//DeferredRenderer(materialManager, engineContext),
                                           override val renderManager: RenderManager = RenderManager(engineContext, engineContext.renderStateManager, renderer, materialManager),
                                           override val managerContext: ManagerContext<OpenGl> = ManagerContextImpl(engineContext = engineContext, renderManager = renderManager)) : ManagerContext<OpenGl> by managerContext, Engine<OpenGl> {

    val updateConsumer = Consumer<Float> { this@EngineImpl.update(it) }
    val updateThread: UpdateThread = UpdateThread(updateConsumer, "Update", TimeUnit.MILLISECONDS.toSeconds(8).toFloat())

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

    val fpsCounter: FPSCounter
        get() = updateThread.fpsCounter

    fun startSimulation() {
        updateThread.start()
    }

    fun update(deltaSeconds: Float) {
        try {
            engineContext.commandQueue.executeCommands()
            inputUpdater.setReadyForExecution()
            while(inputUpdater.isReadyForExecution()){ }
            managerContext.managers.update(deltaSeconds)
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

    fun destroy() {
        System.exit(0)
    }

    companion object {

        private val LOGGER = Logger.getLogger(Engine::class.java.name)

        @JvmStatic
        fun main(args: Array<String>) {

            var gameDir = GAMEDIR_NAME
            var debug = true
            for (string in args) {
                when {
                    "debug=false" == string -> debug = false
                    string.startsWith("gameDir=") -> gameDir = string.replace("gameDir=", "")
                    "fullhd" == string -> {
                        Config.getInstance().width = 1920
                        Config.getInstance().height = 1080
                    }
                }
            }

            Config.getInstance().gameDir = gameDir
            val engine = EngineImpl()
            if (debug) {
                DebugFrame(engine)
            }

            try {
                val initScript = JavaComponent(String(Files.readAllBytes(engine.directoryManager.gameInitScript.toPath())))
                initScript.init(engine)
                initScript.initWithEngine(engine)
                println("InitScript initialized")
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }

        @JvmStatic
        fun create() = EngineImpl()
    }

}

