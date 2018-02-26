package de.hanno.hpengine.engine

import com.alee.laf.WebLookAndFeel
import com.alee.utils.SwingUtils
import com.google.common.eventbus.Subscribe
import de.hanno.hpengine.engine.DirectoryManager.GAMEDIR_NAME
import de.hanno.hpengine.engine.component.JavaComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.*
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.event.bus.MBassadorEventBus
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.renderer.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.Renderer
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.manager.SimpleManagerRegistry
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.physics.PhysicsManager
import de.hanno.hpengine.engine.scene.SceneManager
import de.hanno.hpengine.engine.threads.UpdateThread
import de.hanno.hpengine.util.commandqueue.CommandQueue
import de.hanno.hpengine.util.fps.FPSCounter
import de.hanno.hpengine.util.gui.DebugFrame
import net.engio.mbassy.listener.Handler
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

class Engine private constructor(gameDirName: String) : PerFrameCommandProvider {

    val eventBus: EventBus = MBassadorEventBus()
    val gpuContext: GpuContext = GpuContext.create()
    val updateThread: UpdateThread = UpdateThread(this, "Update", MILLISECONDS.toSeconds(8).toFloat())
    private val drawCounter = AtomicInteger(-1)
    val commandQueue = CommandQueue()

    val managers = SimpleManagerRegistry()

    val directoryManager = managers.register(DirectoryManager(gameDirName).apply { initWorkDir() })
    val renderManager = managers.register(RenderManager(this, { RenderState(gpuContext) }))
    val input = Input(this, gpuContext)
    val programManager = managers.register(ProgramManager(this))
    val textureManager = managers.register(TextureManager(eventBus, programManager, gpuContext))
    val materialManager = managers.register(MaterialManager(this, textureManager))

    val sceneManager = SceneManager(this)

    val renderer: Renderer = Renderer.create(this)
    val physicsManager = PhysicsManager(renderer)

    val fpsCounter: FPSCounter
        get() = updateThread.fpsCounter

    init {
        eventBus.register(this)
        gpuContext.registerPerFrameCommand(this)
        renderer.registerPipelines(renderManager.renderState)
        startSimulation()
        drawCounter.set(0)
        eventBus.post(EngineInitializedEvent())
    }

    fun startSimulation() {
        updateThread.start()

        renderManager.renderThread.start()
    }

    fun update(deltaSeconds: Float) {
        try {
            commandQueue.executeCommands()
            getScene().lightManager.update(this, deltaSeconds, sceneManager.scene.currentCycle)
            getScene().systems.update(deltaSeconds)
            sceneManager.update(deltaSeconds)
            updateRenderState()
            renderManager.drawCycle.getAndIncrement()
            if (!Config.getInstance().isMultithreadedRendering) {
                actuallyDraw()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateRenderState() {
        val scene = sceneManager.scene
        if (scene.entityMovedInCycle() == renderManager.drawCycle.get()) {
            eventBus.post(EntityAddedEvent()) // TODO: Create EntityMovedEvent
        }

        if (gpuContext.isSignaled(renderManager.renderState.currentWriteState.gpuCommandSync)) {
            val directionalLight = getScene().lightManager.directionalLight
            renderManager.renderState.currentWriteState.init(renderManager.vertexIndexBufferStatic, renderManager.vertexIndexBufferAnimated, getScene().modelComponentSystem.joints, sceneManager.activeCamera, scene.entityMovedInCycle(), getScene().lightManager.directionalLightMovedInCycle, scene.pointLightMovedInCycle(), scene.isInitiallyDrawn, scene.minMax[0], scene.minMax[1], renderManager.drawCycle.get(), getScene().lightManager.directionalLight.getEntity().viewMatrixAsBuffer, directionalLight.projectionMatrixAsBuffer, directionalLight.viewProjectionMatrixAsBuffer, directionalLight.scatterFactor, directionalLight.direction, directionalLight.color, scene.entityAddedInCycle)
            scene.extract(renderManager.renderState.currentWriteState)
            renderManager.renderState.update()
            renderManager.renderState.currentWriteState.cycle = renderManager.drawCycle.get()
        }
    }

    fun actuallyDraw() {
        drawCounter.compareAndSet(1,0)
    }

    @Subscribe
    @Handler
    fun handle(event: MaterialAddedEvent) {
        renderManager.bufferMaterialsActionRef.request()
    }

    @Subscribe
    @Handler
    fun handle(event: MaterialChangedEvent) {
        renderManager.renderState.addCommand { renderStateX ->
            if (event.material.isPresent) {
                //                renderStateX.bufferMaterial(event.getMaterials().get());
                renderManager.bufferMaterialsActionRef.request()
            } else {
                renderManager.bufferMaterialsActionRef.request()
            }
        }
    }

    private fun destroyOpenGL() {
        gpuContext.gpuThread.stopRequested = true
    }

    fun destroy() {
        LOGGER.info("Finalize renderer")
        destroyOpenGL()
        System.exit(0)
    }

    override fun getDrawCommand() = renderManager.drawRunnable
    override fun isReadyForExecution() = drawCounter.get() == 0
    override fun postRun() { drawCounter.getAndIncrement() }


    companion object {

        private val LOGGER = Logger.getLogger(Engine::class.java.name)

        @JvmStatic
        fun main(args: Array<String>) {

            var sceneName: String? = null
            var gameDir = GAMEDIR_NAME
            var debug = true
            for (string in args) {
                if ("debug=false" == string) {
                    debug = false
                } else if (string.startsWith("gameDir=")) {
                    gameDir = string.replace("gameDir=", "")
                } else if ("fullhd" == string) {
                    Config.getInstance().width = 1920
                    Config.getInstance().height = 1080
                } else {
                    sceneName = string
                    break
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
        @JvmStatic fun create(gameDirName: String = GAMEDIR_NAME) = Engine(gameDirName)
    }

    fun getScene() = sceneManager.scene
}

