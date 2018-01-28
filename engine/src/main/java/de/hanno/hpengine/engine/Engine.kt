package de.hanno.hpengine.engine

import com.alee.laf.WebLookAndFeel
import com.alee.utils.SwingUtils
import com.google.common.eventbus.Subscribe
import de.hanno.hpengine.engine.DirectoryManager.GAMEDIR_NAME
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.camera.MovableCamera
import de.hanno.hpengine.engine.component.JavaComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.*
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.RenderSystem
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext
import de.hanno.hpengine.engine.graphics.renderer.Renderer
import de.hanno.hpengine.engine.graphics.shader.ProgramFactory
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.model.EntityFactory
import de.hanno.hpengine.engine.model.material.MaterialFactory
import de.hanno.hpengine.engine.model.texture.TextureFactory
import de.hanno.hpengine.engine.physics.PhysicsFactory
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.threads.UpdateThread
import de.hanno.hpengine.util.commandqueue.CommandQueue
import de.hanno.hpengine.util.fps.FPSCounter
import de.hanno.hpengine.util.gui.DebugFrame
import de.hanno.hpengine.util.script.ScriptManager
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import de.hanno.hpengine.util.stopwatch.StopWatch
import net.engio.mbassy.listener.Handler
import org.joml.Vector3f
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

class Engine private constructor() : HighFrequencyCommandProvider {

    init {
        GraphicsContext.initGpuContext()
    }

    val renderSystem by lazy { RenderSystem() }

    var updateThread: UpdateThread? = null
        private set

    private val drawCounter = AtomicInteger(-1)

    val commandQueue = CommandQueue()
    val scriptManager: ScriptManager by lazy { ScriptManager.getInstance() }
    val physicsFactory: PhysicsFactory by lazy { PhysicsFactory() }
    var scene: Scene = Scene()
        set(value) {
            physicsFactory.clearWorld()
            field = value
            GraphicsContext.getInstance().execute({
                StopWatch.getInstance().start("Scene init")
                value.init()
                StopWatch.getInstance().stopAndPrintMS()
            }, true)
            restoreWorldCamera()
            renderSystem.renderState.addCommand { renderState1 ->
                renderState1.setVertexIndexBufferStatic(value.vertexIndexBufferStatic)
                renderState1.setVertexIndexBufferAnimated(value.vertexIndexBufferAnimated)
            }
        }
    private val camera = MovableCamera()
    var activeCamera: Camera? = null
    @Volatile
    var isInitialized: Boolean = false

    val fpsCounter: FPSCounter
        get() = updateThread!!.fpsCounter

    private fun initialize(gamedirName: String) {
        eventBus.register(this)
        directoryManager = DirectoryManager(gamedirName)
        directoryManager.initWorkDir()
        GraphicsContext.getInstance().registerHighFrequencyCommand(this)

        EntityFactory.create()
        ProgramFactory.init()
        TextureFactory.init()
        MaterialFactory.init()
        Renderer.init(Config.getInstance().rendererClass)
        //        Renderer.init(SimpleTextureRenderer.class);
        //        MaterialFactory.getInstance().initDefaultMaterials();

        ScriptManager.getInstance().defineGlobals()

        Renderer.getInstance().registerPipelines(renderSystem.renderState)
        camera.initialize()
        camera.setTranslation(Vector3f(0f, 20f, 0f))
        activeCamera = camera
        scene = Scene()
        scene.init()
        _instance.startSimulation()
        isInitialized = true
        drawCounter.set(0)
        eventBus.post(EngineInitializedEvent())
    }

    fun startSimulation() {
        updateThread = UpdateThread("Update", MILLISECONDS.toSeconds(8).toFloat())
        updateThread!!.start()

        renderSystem.renderThread.start()
    }

    fun update(seconds: Float) {
        try {
            commandQueue.executeCommands()
            activeCamera!!.update(seconds)
            scene!!.setCurrentCycle(renderSystem.drawCycle.get())
            scene!!.update(seconds)
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
        if (scene.entityMovedInCycle() == renderSystem.drawCycle.get()) {
            renderSystem.renderState.requestSingletonAction(0)
        }

        if (GraphicsContext.getInstance().isSignaled(renderSystem.renderState.currentWriteState.gpuCommandSync)) {
            val directionalLightCamera = scene.directionalLight
            renderSystem.renderState.currentWriteState.init(scene.vertexIndexBufferStatic, scene.vertexIndexBufferAnimated, scene.joints, activeCamera, scene.entityMovedInCycle(), scene!!.directionalLightMovedInCycle(), scene!!.pointLightMovedInCycle(), scene!!.isInitiallyDrawn, scene!!.minMax[0], scene!!.minMax[1], renderSystem.drawCycle.get(), directionalLightCamera.viewMatrixAsBuffer, directionalLightCamera.projectionMatrixAsBuffer, directionalLightCamera.viewProjectionMatrixAsBuffer, scene.directionalLight.scatterFactor, scene.directionalLight.direction, scene.directionalLight.color, scene.entityAddedInCycle)
            scene.addRenderBatches(this.activeCamera, renderSystem.renderState.currentWriteState)
            renderSystem.renderState.update()
            renderSystem.renderState.currentWriteState.cycle = renderSystem.drawCycle.get()
        }
    }

    fun actuallyDraw() {
        while (drawCounter.get() != 1) {
        }
        drawCounter.getAndDecrement()
    }

    fun restoreWorldCamera() {
        this.activeCamera = camera
    }

    @Subscribe
    @Handler
    fun handle(e: EntityAddedEvent) {
        scene.setUpdateCache(true)
        renderSystem.renderState.addCommand { renderStateX -> renderStateX.bufferEntities(scene.entities) }
    }

    @Subscribe
    @Handler
    fun handle(e: SceneInitEvent) {
        renderSystem.renderState.addCommand { renderStateX -> renderStateX.bufferEntities(scene.entities) }
    }

    @Subscribe
    @Handler
    fun handle(event: EntityChangedMaterialEvent) {
        val entity = event.entity
        //            buffer(entity);
        renderSystem.renderState.addCommand { renderStateX -> renderStateX.bufferEntities(scene.entities) }
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
        GraphicsContext.getInstance().drawThread.stopRequested = true
        //        try {
        //            Display.destroy();
        //        } catch (IllegalStateException e) {
        //            e.printStackTrace();
        //        }
    }

    fun destroy() {
        LOGGER.info("Finalize renderer")
        destroyOpenGL()
        System.exit(0)
    }

    fun getDirectoryManager(): DirectoryManager? {
        return directoryManager
    }

    override fun getDrawCommand(): Runnable {
        return renderSystem.drawRunnable
    }

    override fun getAtomicCounter(): AtomicInteger {
        return drawCounter
    }

    companion object {
        @Volatile private lateinit var _instance: Engine
        private lateinit var directoryManager: DirectoryManager

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
            directoryManager = DirectoryManager(gameDir)
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
                Renderer.getInstance()
                val scene = Scene.read(sceneName)
                Engine.getInstance().scene = scene
            }

            try {
                val initScript = JavaComponent(String(Files.readAllBytes(directoryManager.gameInitScript.toPath())))
                initScript.init()
                println("initScript = " + initScript.isInitialized)
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }

        @JvmOverloads
        @JvmStatic fun init(gameDirName: String = GAMEDIR_NAME) {
            _instance = Engine()
            _instance!!.initialize(gameDirName)
        }

        @JvmStatic val eventBus: EventBus
            get() = EventBus.getInstance()
    }
}
