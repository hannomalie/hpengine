package de.hanno.hpengine.engine

import com.alee.laf.WebLookAndFeel
import com.alee.utils.SwingUtils
import de.hanno.hpengine.engine.DirectoryManager.GAMEDIR_NAME
import de.hanno.hpengine.engine.backend.EngineContextImpl
import de.hanno.hpengine.engine.backend.ManagerContext
import de.hanno.hpengine.engine.backend.ManagerContextImpl
import de.hanno.hpengine.engine.component.JavaComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.EngineInitializedEvent
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.renderer.DeferredRenderer
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.scene.SceneManager
import de.hanno.hpengine.engine.threads.UpdateThread
import de.hanno.hpengine.engine.threads.UpdateThread.isUpdateThread
import de.hanno.hpengine.util.commandqueue.CommandQueue
import de.hanno.hpengine.util.fps.FPSCounter
import de.hanno.hpengine.util.gui.DebugFrame
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.logging.Logger

interface Engine: ManagerContext {
    val managerContext: ManagerContext
    val sceneManager: SceneManager

    val scene
        get() = sceneManager.scene
}

class EngineImpl private constructor(override val engineContext: EngineContextImpl = EngineContextImpl(CommandQueue({isUpdateThread()})),
                                     val materialManager: MaterialManager = MaterialManager(engineContext),
                                     val renderer: DeferredRenderer = DeferredRenderer(materialManager, engineContext),
                                     override val renderManager: RenderManager = RenderManager(engineContext, engineContext.renderStateManager, renderer),
                                     override val managerContext: ManagerContextImpl = ManagerContextImpl(engineContext = engineContext, renderManager = renderManager)) : ManagerContext by managerContext, Engine {

    val updateConsumer = Consumer<Float> { this@EngineImpl.update(it) }
    val updateThread: UpdateThread = UpdateThread(updateConsumer, "Update", TimeUnit.MILLISECONDS.toSeconds(8).toFloat())

    override val sceneManager = managerContext.managers.register(SceneManager(managerContext))

    init {
        engineContext.eventBus.register(this)
        startSimulation()
        engineContext.eventBus.post(EngineInitializedEvent())
    }

    val fpsCounter: FPSCounter
        get() = updateThread.fpsCounter

    fun startSimulation() {
        updateThread.start()
        renderManager.renderThread.start()
    }

    fun update(deltaSeconds: Float) {
        try {
            engineContext.commandQueue.executeCommands()
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
                renderState.currentWriteState.deltaInS = renderManager.getDeltaInS().toFloat()
                scene.extract(renderState.currentWriteState)
                renderState.update()
            }
            drawCycle.getAndIncrement()
        }
    }

    fun destroy() {
        gpuContext.gpuThread.stopRequested = true
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
            try {
                SwingUtils.invokeAndWait { WebLookAndFeel.install() }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } catch (e: InvocationTargetException) {
                e.printStackTrace()
            }

            Config.getInstance().gameDir = gameDir
            val engine = create()
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

        @JvmOverloads
        @JvmStatic
        fun create() = EngineImpl()
    }

}

