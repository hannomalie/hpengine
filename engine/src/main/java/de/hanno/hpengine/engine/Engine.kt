package de.hanno.hpengine.engine

import com.alee.laf.WebLookAndFeel
import com.alee.utils.SwingUtils
import com.google.common.eventbus.Subscribe
import de.hanno.hpengine.engine.DirectoryManager.GAMEDIR_NAME
import de.hanno.hpengine.engine.component.JavaComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.*
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.RenderSystem
import de.hanno.hpengine.engine.graphics.light.LightFactory
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext
import de.hanno.hpengine.engine.graphics.renderer.Renderer
import de.hanno.hpengine.engine.graphics.shader.ProgramFactory
import de.hanno.hpengine.engine.model.EntityFactory
import de.hanno.hpengine.engine.model.material.MaterialFactory
import de.hanno.hpengine.engine.model.texture.TextureFactory
import de.hanno.hpengine.engine.physics.PhysicsFactory
import de.hanno.hpengine.engine.scene.EnvironmentProbeFactory
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.SceneManager
import de.hanno.hpengine.engine.threads.UpdateThread
import de.hanno.hpengine.util.commandqueue.CommandQueue
import de.hanno.hpengine.util.fps.FPSCounter
import de.hanno.hpengine.util.gui.DebugFrame
import de.hanno.hpengine.util.script.ScriptManager
import net.engio.mbassy.listener.Handler
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

class Engine private constructor(gameDirName: String) : PerFrameCommandProvider {

    val gpuContext = GraphicsContext.initGpuContext()
    val updateThread: UpdateThread = UpdateThread("Update", MILLISECONDS.toSeconds(8).toFloat())
    private val drawCounter = AtomicInteger(-1)
    private val commandQueue = CommandQueue()

    val entityFactory = EntityFactory()
    val directoryManager = DirectoryManager(gameDirName).apply { initWorkDir() }
    val renderSystem by lazy { RenderSystem() }
    val environmentProbeFactory by lazy { EnvironmentProbeFactory()}
    val lightFactory by lazy { LightFactory() }
    val sceneManager = SceneManager()
    val scriptManager by lazy { ScriptManager().apply { defineGlobals(this@Engine) } }
    val physicsFactory by lazy { PhysicsFactory() }
    val programFactory by lazy { ProgramFactory() }
    val textureFactory by lazy { TextureFactory(this@Engine) }
    val materialFactory by lazy { MaterialFactory() }
    val renderer: Renderer by lazy { Renderer.create(Config.getInstance().rendererClass) }

    @Volatile var isInitialized: Boolean = false

    val fpsCounter: FPSCounter
        get() = updateThread.fpsCounter

    private fun initialize() {
        eventBus.register(this)
        gpuContext.registerPerFrameCommand(this)

        renderer.registerPipelines(renderSystem.renderState)
        _instance.startSimulation()
        isInitialized = true
        drawCounter.set(0)
        eventBus.post(EngineInitializedEvent())
    }

    fun startSimulation() {
        updateThread.start()

        renderSystem.renderThread.start()
    }

    fun update(seconds: Float) {
        try {
            commandQueue.executeCommands()
            sceneManager.activeCamera.update(seconds)
            sceneManager.scene.setCurrentCycle(renderSystem.drawCycle.get())
            sceneManager.scene.update(seconds)
            updateRenderState()
            renderSystem.drawCycle.getAndIncrement()
            if (!Config.getInstance().isMultithreadedRendering) {
                actuallyDraw()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun updateRenderState() {
        val scene = sceneManager.scene
        if (scene.entityMovedInCycle() == renderSystem.drawCycle.get()) {
            renderSystem.renderState.requestSingletonAction(0)
        }

        if (gpuContext.isSignaled(renderSystem.renderState.currentWriteState.gpuCommandSync)) {
            val directionalLightCamera = scene.directionalLight
            renderSystem.renderState.currentWriteState.init(renderSystem.vertexIndexBufferStatic, renderSystem.vertexIndexBufferAnimated, scene.joints, sceneManager.activeCamera, scene.entityMovedInCycle(), scene!!.directionalLightMovedInCycle(), scene!!.pointLightMovedInCycle(), scene!!.isInitiallyDrawn, scene!!.minMax[0], scene!!.minMax[1], renderSystem.drawCycle.get(), directionalLightCamera.viewMatrixAsBuffer, directionalLightCamera.projectionMatrixAsBuffer, directionalLightCamera.viewProjectionMatrixAsBuffer, scene.directionalLight.scatterFactor, scene.directionalLight.direction, scene.directionalLight.color, scene.entityAddedInCycle)
            scene.addRenderBatches(sceneManager.activeCamera, renderSystem.renderState.currentWriteState)
            renderSystem.renderState.update()
            renderSystem.renderState.currentWriteState.cycle = renderSystem.drawCycle.get()
        }
    }

    fun actuallyDraw() {
        drawCounter.compareAndSet(1,0)
    }

    @Subscribe
    @Handler
    fun handle(e: EntityAddedEvent) {
        sceneManager.scene.setUpdateCache(true)
        renderSystem.renderState.addCommand { renderStateX -> renderStateX.bufferEntities(sceneManager.scene.entities) }
    }

    @Subscribe
    @Handler
    fun handle(e: SceneInitEvent) {
        renderSystem.renderState.addCommand { renderStateX -> renderStateX.bufferEntities(sceneManager.scene.entities) }
    }

    @Subscribe
    @Handler
    fun handle(event: EntityChangedMaterialEvent) {
        val entity = event.entity
        //            buffer(entity);
        renderSystem.renderState.addCommand { renderStateX -> renderStateX.bufferEntities(sceneManager.scene.entities) }
    }

    @Subscribe
    @Handler
    fun handle(event: MaterialAddedEvent) {
        renderSystem.renderState.addCommand { renderStateX -> renderStateX.bufferMaterials() }
    }

    @Subscribe
    @Handler
    fun handle(event: MaterialChangedEvent) {
        renderSystem.renderState.addCommand { renderStateX ->
            if (event.material.isPresent) {
                //                renderStateX.bufferMaterial(event.getMaterials().get());
                renderStateX.bufferMaterials()
            } else {
                renderStateX.bufferMaterials()
            }
        }
    }

    private fun destroyOpenGL() {
        gpuContext.drawThread.stopRequested = true
    }

    fun destroy() {
        LOGGER.info("Finalize renderer")
        destroyOpenGL()
        System.exit(0)
    }

    override fun getDrawCommand() = renderSystem.drawRunnable
    override fun isReadyForExecution() = drawCounter.get() == 0
    override fun postRun() { drawCounter.getAndIncrement() }


    companion object {
        @Volatile private lateinit var _instance: Engine

        @JvmStatic fun getInstance(): Engine {
            return _instance
        }

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

            init(gameDir)
            if (debug) {
                DebugFrame()
            }
            if (sceneName != null) {
                val scene = Scene.read(sceneName)
                Engine.getInstance().sceneManager.scene = scene
            }

            try {
                val initScript = JavaComponent(String(Files.readAllBytes(Engine.getInstance().directoryManager.gameInitScript.toPath())))
                initScript.init()
                println("initScript = " + initScript.isInitialized)
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }

        @JvmOverloads
        @JvmStatic fun init(gameDirName: String = GAMEDIR_NAME) {
            _instance = Engine(gameDirName)
            _instance!!.initialize()
        }

        @JvmStatic val eventBus: EventBus
            get() = EventBus.getInstance()
    }
}
