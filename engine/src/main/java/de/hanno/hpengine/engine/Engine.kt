package de.hanno.hpengine.engine

import com.alee.laf.WebLookAndFeel
import com.alee.utils.SwingUtils
import com.google.common.eventbus.Subscribe
import de.hanno.hpengine.engine.DirectoryManager.GAMEDIR_NAME
import de.hanno.hpengine.engine.camera.CameraComponentSystem
import de.hanno.hpengine.engine.camera.InputComponentSystem
import de.hanno.hpengine.engine.component.JavaComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.*
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.event.bus.MBassadorEventBus
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.light.LightManager
import de.hanno.hpengine.engine.graphics.renderer.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.Renderer
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.manager.Registry
import de.hanno.hpengine.engine.manager.SimpleRegistry
import de.hanno.hpengine.engine.entity.EntityManager
import de.hanno.hpengine.engine.model.ModelComponentSystem
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.physics.PhysicsManager
import de.hanno.hpengine.engine.scene.EnvironmentProbeManager
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

    val eventBus: EventBus = MBassadorEventBus()
    val gpuContext: GpuContext = GpuContext.create()
    val updateThread: UpdateThread = UpdateThread(this, "Update", MILLISECONDS.toSeconds(8).toFloat())
    private val drawCounter = AtomicInteger(-1)
    private val commandQueue = CommandQueue()

    val systems: Registry = SimpleRegistry()

    val entityManager = EntityManager(this, eventBus)
    val directoryManager = DirectoryManager(gameDirName).apply { initWorkDir() }
    val renderManager = RenderManager(this)
    val input = Input(this, gpuContext)
    val environmentProbeManager = EnvironmentProbeManager(this)
    val programManager = ProgramManager(this)
    val textureManager = TextureManager(eventBus, programManager, gpuContext)
    val materialManager = MaterialManager(this, textureManager)
    val cameraComponentSystem = systems.register(CameraComponentSystem(this))
    val inputComponentSystem = systems.register(InputComponentSystem(this))
    val modelComponentSystem = systems.register(ModelComponentSystem(this))
    val sceneManager = SceneManager(this)
    val lightManager = LightManager(eventBus, materialManager, sceneManager, gpuContext, programManager, inputComponentSystem, modelComponentSystem)
    val scriptManager = ScriptManager().apply { defineGlobals(this@Engine) }
    val renderer: Renderer = Renderer.create(this)
    val physicsManager = PhysicsManager(renderer)

    @Volatile var isInitialized: Boolean = false

    val fpsCounter: FPSCounter
        get() = updateThread.fpsCounter

    init {
        eventBus.register(this)
        gpuContext.registerPerFrameCommand(this)

        renderer.registerPipelines(renderManager.renderState)
        startSimulation()
        isInitialized = true
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
            lightManager.update(this, deltaSeconds, sceneManager.scene.currentCycle)
            systems.update(deltaSeconds)
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
            renderManager.renderState.requestSingletonAction(0)
        }

        if (gpuContext.isSignaled(renderManager.renderState.currentWriteState.gpuCommandSync)) {
            val directionalLight = lightManager.directionalLight
            renderManager.renderState.currentWriteState.init(renderManager.vertexIndexBufferStatic, renderManager.vertexIndexBufferAnimated, modelComponentSystem.joints, sceneManager.activeCamera, scene.entityMovedInCycle(), lightManager.directionalLightMovedInCycle, scene.pointLightMovedInCycle(), scene.isInitiallyDrawn, scene.minMax[0], scene.minMax[1], renderManager.drawCycle.get(), lightManager.directionalLight.getEntity().viewMatrixAsBuffer, directionalLight.projectionMatrixAsBuffer, directionalLight.viewProjectionMatrixAsBuffer, directionalLight.scatterFactor, directionalLight.direction, directionalLight.color, scene.entityAddedInCycle)
            scene.addRenderBatches(this, sceneManager.activeCamera, renderManager.renderState.currentWriteState)
            renderManager.renderState.update()
            renderManager.renderState.currentWriteState.cycle = renderManager.drawCycle.get()
        }
    }

    fun actuallyDraw() {
        drawCounter.compareAndSet(1,0)
    }

    @Subscribe
    @Handler
    fun handle(e: EntityAddedEvent) {
        modelComponentSystem.updateCache = true
        renderManager.renderState.addCommand { renderStateX -> renderStateX.bufferEntities(modelComponentSystem.components) }
    }

    @Subscribe
    @Handler
    fun handle(e: SceneInitEvent) {
        renderManager.renderState.addCommand { renderStateX -> renderStateX.bufferEntities(modelComponentSystem.components) }
    }

    @Subscribe
    @Handler
    fun handle(event: EntityChangedMaterialEvent) {
        val entity = event.entity
        //            buffer(entity);
        renderManager.renderState.addCommand { renderStateX -> renderStateX.bufferEntities(modelComponentSystem.components) }
    }

    @Subscribe
    @Handler
    fun handle(event: MaterialAddedEvent) {
        renderManager.renderState.addCommand { renderStateX -> renderStateX.bufferMaterials(this@Engine) }
    }

    @Subscribe
    @Handler
    fun handle(event: MaterialChangedEvent) {
        renderManager.renderState.addCommand { renderStateX ->
            if (event.material.isPresent) {
                //                renderStateX.bufferMaterial(event.getMaterials().get());
                renderStateX.bufferMaterials(this@Engine)
            } else {
                renderStateX.bufferMaterials(this@Engine)
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
                println("initScript = " + initScript.isInitialized)
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }

        @JvmOverloads
        @JvmStatic fun create(gameDirName: String = GAMEDIR_NAME) = Engine(gameDirName)
    }

}

