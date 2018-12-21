package de.hanno.hpengine.engine

import com.alee.laf.WebLookAndFeel
import com.alee.utils.SwingUtils
import de.hanno.hpengine.engine.DirectoryManager.GAMEDIR_NAME
import de.hanno.hpengine.engine.component.JavaComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.EngineInitializedEvent
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.event.bus.MBassadorEventBus
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.renderer.DeferredRenderer
import de.hanno.hpengine.engine.graphics.renderer.Renderer
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.manager.SimpleManagerRegistry
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.physics.PhysicsManager
import de.hanno.hpengine.engine.scene.EnvironmentProbeManager
import de.hanno.hpengine.engine.scene.SceneManager
import de.hanno.hpengine.engine.threads.UpdateThread
import de.hanno.hpengine.engine.threads.UpdateThread.isUpdateThread
import de.hanno.hpengine.util.commandqueue.CommandQueue
import de.hanno.hpengine.util.fps.FPSCounter
import de.hanno.hpengine.util.gui.DebugFrame
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.logging.Logger

class Engine private constructor(gameDirName: String) {

    val eventBus: EventBus = MBassadorEventBus()
    val gpuContext: GpuContext = GpuContext.create()
    val input = Input(this, gpuContext)
    val updateThread: UpdateThread = UpdateThread(this, "Update", MILLISECONDS.toSeconds(8).toFloat())
    val commandQueue = object: CommandQueue() {
        override fun executeDirectly() = isUpdateThread()
    }

    val managers = SimpleManagerRegistry()

    val directoryManager = managers.register(DirectoryManager(gameDirName).apply { initWorkDir() })
    val renderManager = managers.register(RenderManager(this, gpuContext, { RenderState(gpuContext) }))
    val programManager = managers.register(ProgramManager(gpuContext, eventBus))
    val textureManager = managers.register(TextureManager(eventBus, programManager, gpuContext))
    val environmentProbeManager = managers.register(EnvironmentProbeManager(this))

    val sceneManager = managers.register(SceneManager(this))
    val renderer: Renderer = DeferredRenderer(this)

    val physicsManager = PhysicsManager(renderer)

    init {
        eventBus.register(this)
        startSimulation()
        eventBus.post(EngineInitializedEvent())
    }

    val fpsCounter: FPSCounter
        get() = updateThread.fpsCounter

    fun startSimulation() {
        updateThread.start()
        renderManager.renderThread.start()
    }

    fun update(deltaSeconds: Float) {
        try {
            commandQueue.executeCommands()
            managers.update(deltaSeconds)
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
                scene.extract(renderState.currentWriteState)
                renderState.update()
            }
            drawCycle.getAndIncrement()
        }
    }

    fun actuallyDraw() {
        renderManager.perFrameCommand.setReadyForExecution()
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

            val engine = create(gameDir)
            if (debug) {
                DebugFrame(engine)
            }

            try {
                val initScript = JavaComponent(String(Files.readAllBytes(engine.directoryManager.gameInitScript.toPath())))
                initScript.init(engine)
                println("InitScript initialized")
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }

        @JvmOverloads
        @JvmStatic
        fun create(gameDirName: String = GAMEDIR_NAME) = Engine(gameDirName)
    }

    fun getScene() = sceneManager.scene

}

