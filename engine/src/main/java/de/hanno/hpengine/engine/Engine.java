package de.hanno.hpengine.engine;

import com.alee.laf.WebLookAndFeel;
import com.alee.utils.SwingUtils;
import com.google.common.eventbus.Subscribe;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.camera.MovableCamera;
import de.hanno.hpengine.engine.component.JavaComponent;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.event.*;
import de.hanno.hpengine.engine.event.bus.EventBus;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
import de.hanno.hpengine.engine.graphics.renderer.Renderer;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.engine.graphics.shader.ProgramFactory;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.state.RenderStateRecorder;
import de.hanno.hpengine.engine.graphics.state.SimpleRenderStateRecorder;
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer;
import de.hanno.hpengine.engine.input.Input;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.EntityFactory;
import de.hanno.hpengine.engine.model.material.MaterialFactory;
import de.hanno.hpengine.engine.model.texture.TextureFactory;
import de.hanno.hpengine.engine.physics.PhysicsFactory;
import de.hanno.hpengine.engine.scene.Scene;
import de.hanno.hpengine.engine.threads.RenderThread;
import de.hanno.hpengine.engine.threads.UpdateThread;
import de.hanno.hpengine.util.fps.FPSCounter;
import de.hanno.hpengine.util.gui.DebugFrame;
import de.hanno.hpengine.util.script.ScriptManager;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import de.hanno.hpengine.util.stopwatch.StopWatch;
import net.engio.mbassy.listener.Handler;
import org.joml.Vector3f;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static de.hanno.hpengine.engine.DirectoryManager.GAMEDIR_NAME;

public class Engine implements HighFrequencyCommandProvider {

    private static volatile Engine instance = null;
    private static DirectoryManager directoryManager;

    private RenderStateRecorder recorder;
    private UpdateThread updateThread;
    private RenderThread renderThread;
    private volatile TripleBuffer<RenderState> renderState;

    private volatile AtomicLong drawCycle = new AtomicLong();
    private long cpuGpuSyncTimeNs;
    private AtomicInteger drawCounter = new AtomicInteger(-1);

    public static Engine getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Call Engine.init() before using it");
        }
        return instance;
    }

    private ScriptManager scriptManager;
    private PhysicsFactory physicsFactory;
    private Scene scene;
    private final Camera camera = new MovableCamera();
    private Camera activeCamera;

    private static final Logger LOGGER = Logger.getLogger(Engine.class.getName());
    private volatile boolean initialized;

    public static void main(String[] args) {

        String sceneName = null;
        String gameDir = GAMEDIR_NAME;
        boolean debug = true;
        for (String string : args) {
            if ("debug=false".equals(string)) {
                debug = false;
            } else if (string.startsWith("gameDir=")) {
                gameDir = string.replace("gameDir=", "");
            } else if ("fullhd".equals(string)) {
                Config.getInstance().setWidth(1920);
                Config.getInstance().setHeight(1080);
            } else {
                sceneName = string;
                break;
            }
        }
        directoryManager = new DirectoryManager(gameDir);
        try {
            SwingUtils.invokeAndWait(() -> WebLookAndFeel.install());
        } catch (InterruptedException | InvocationTargetException e) {
            e.printStackTrace();
        }

        init(gameDir);
        if (debug) {
            new DebugFrame();
        }
        if (sceneName != null) {
            Renderer.getInstance();
            Scene scene = Scene.read(sceneName);
            Engine.getInstance().setScene(scene);
        }

        try {
            JavaComponent initScript = new JavaComponent(new String(Files.readAllBytes(directoryManager.getGameInitScript().toPath())));
            initScript.init();
            System.out.println("initScript = " + initScript.isInitialized());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void init() {
        init(GAMEDIR_NAME);
    }
    public static void init(String gameDirName) {
        if (instance != null) {
            return;
        }
        instance = new Engine();
        instance.initialize(gameDirName);
    }

    private Engine() {
    }

    private void initialize(String gamedirName) {
        recorder = new SimpleRenderStateRecorder();
        getEventBus().register(this);
        directoryManager = new DirectoryManager(gamedirName);
        directoryManager.initWorkDir();
        GraphicsContext.initGpuContext();
        GraphicsContext.getInstance().registerHighFrequencyCommand(this);

        EntityFactory.create();
        ProgramFactory.init();
        TextureFactory.init();
        MaterialFactory.init();
        Renderer.init(Config.getInstance().getRendererClass());
//        Renderer.init(SimpleTextureRenderer.class);
//        MaterialFactory.getInstance().initDefaultMaterials();

        scriptManager = ScriptManager.getInstance();
        physicsFactory = new PhysicsFactory();
        ScriptManager.getInstance().defineGlobals();

        renderState = new TripleBuffer<>(new RenderState(), new RenderState(), new RenderState(), (renderState) -> {
            renderState.bufferEntites(Engine.getInstance().getScene().getEntities());
        });
        Renderer.getInstance().registerPipelines(renderState);
        camera.init();
        camera.setTranslation(new Vector3f(0, 20, 0));
        activeCamera = camera;
        scene = new Scene();
        scene.init();
        instance.startSimulation();
        initialized = true;
        drawCounter.set(0);
        getEventBus().post(new EngineInitializedEvent());
    }

    public void startSimulation() {
        updateThread = new UpdateThread("Update", 0.008f);
        updateThread.start();

        renderThread = new RenderThread("Render");
        renderThread.start();
    }

    public void update(float seconds) {
        try {
            activeCamera.update(seconds);
            scene.setCurrentCycle(drawCycle.get());
            scene.update(seconds);
            updateRenderState();
            drawCycle.getAndIncrement();
            if(!Config.getInstance().isMultithreadedRendering()) {
                actuallyDraw();
            }
        } catch (Exception e) {} // Yea, i know...
    }

    private void updateRenderState() {
        if (scene.entityMovedInCycle() == drawCycle.get()) {
            renderState.requestSingletonAction(0);
        }

        if(GraphicsContext.getInstance().isSignaled(renderState.getCurrentWriteState().getGpuCommandSync())) {
            Camera directionalLightCamera = scene.getDirectionalLight();
            renderState.getCurrentWriteState().init(scene.getVertexIndexBuffer(), getActiveCamera(), scene.entityMovedInCycle(), scene.directionalLightMovedInCycle(), scene.pointLightMovedInCycle(), scene.isInitiallyDrawn(), scene.getMinMax()[0], scene.getMinMax()[1], drawCycle.get(), directionalLightCamera.getViewMatrixAsBuffer(), directionalLightCamera.getProjectionMatrixAsBuffer(), directionalLightCamera.getViewProjectionMatrixAsBuffer(), scene.getDirectionalLight().getScatterFactor(), scene.getDirectionalLight().getDirection(), scene.getDirectionalLight().getColor(), scene.getEntityAddedInCycle());
            scene.addRenderBatches(this.activeCamera, renderState.getCurrentWriteState());
            renderState.update();
        }
    }

    private Runnable drawRunnable = new Runnable() {
        boolean lastTimeSwapped = true;
        @Override
        public void run() {
            renderState.startRead();

            if(lastTimeSwapped) {
                Input.update();
                Renderer.getInstance().startFrame();
                GPUProfiler.start("Prepare state");
                recorder.add(renderState.getCurrentReadState());
                DrawResult latestDrawResult = renderState.getCurrentReadState().latestDrawResult;
                latestDrawResult.reset();
                GPUProfiler.end();
                Renderer.getInstance().draw(latestDrawResult, renderState.getCurrentReadState());
                latestDrawResult.GPUProfilingResult = GPUProfiler.dumpTimings();
                Renderer.getInstance().endFrame();
                scene.setInitiallyDrawn(true);

                Engine.getEventBus().post(new FrameFinishedEvent(latestDrawResult));
            }
            lastTimeSwapped = renderState.stopRead();
        }
    };

    public void actuallyDraw() {
        while(drawCounter.get() != 1) {
        }
        drawCounter.getAndDecrement();
    }

    public Scene getScene() {
        return scene;
    }

    public void setScene(Scene scene) {
        physicsFactory.clearWorld();
        this.scene = scene;
        GraphicsContext.getInstance().execute(() -> {
            StopWatch.getInstance().start("Scene init");
            scene.init();
            StopWatch.getInstance().stopAndPrintMS();
        }, true);
        restoreWorldCamera();
        renderState.addCommand(renderState1 -> {
            renderState1.setVertexIndexBuffer(scene.getVertexIndexBuffer());
        });
    }

    public Camera getActiveCamera() {
        return activeCamera;
    }

    public void setActiveCamera(Camera newActiveCamera) {
        this.activeCamera = newActiveCamera;
    }

    public void restoreWorldCamera() {
        this.activeCamera = camera;
    }

    public static EventBus getEventBus() {
        return EventBus.getInstance();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public ScriptManager getScriptManager() {
        return scriptManager;
    }

    public FPSCounter getFPSCounter() {
        return updateThread.getFpsCounter();
    }

    @Subscribe
    @Handler
    public void handle(EntityAddedEvent e) {
        scene.setUpdateCache(true);
        renderState.addCommand((renderStateX -> {
            renderStateX.bufferEntites(scene.getEntities());
        }));
    }

    @Subscribe
    @Handler
    public void handle(SceneInitEvent e) {
        renderState.addCommand((renderStateX -> {
            renderStateX.bufferEntites(scene.getEntities());
        }));
    }

    @Subscribe
    @Handler
    public void handle(EntityChangedMaterialEvent event) {
        if(Engine.getInstance().getScene() != null) {
            Entity entity = event.getEntity();
//            buffer(entity);
            renderState.addCommand((renderStateX -> {
                renderStateX.bufferEntites(scene.getEntities());
            }));
        }
    }

    @Subscribe
    @Handler
    public void handle(MaterialAddedEvent event) {
        if(renderState == null) {return;}
        renderState.addCommand(((renderStateX) -> {
            renderStateX.bufferMaterials();
        }));
    }

    @Subscribe
    @Handler
    public void handle(MaterialChangedEvent event) {
        if(renderState == null) {return;}
        renderState.addCommand(((renderStateX) -> {
            if(event.getMaterial().isPresent()) {
//                renderStateX.bufferMaterial(event.getMaterial().get());
                renderStateX.bufferMaterials();
            } else {
                renderStateX.bufferMaterials();
            }
        }));
    }
    public PhysicsFactory getPhysicsFactory() {
        return physicsFactory;
    }

    public TripleBuffer<RenderState> getRenderState() {
        return renderState;
    }

    private void destroyOpenGL() {
        GraphicsContext.getInstance().getDrawThread().stopRequested = true;
//        try {
//            Display.destroy();
//        } catch (IllegalStateException e) {
//            e.printStackTrace();
//        }
    }

    public void destroy() {
        LOGGER.info("Finalize renderer");
        destroyOpenGL();
        System.exit(0);
    }

    public DirectoryManager getDirectoryManager() {
        return directoryManager;
    }

    public UpdateThread getUpdateThread() {
        return updateThread;
    }

    public void setCpuGpuSyncTimeNs(long cpuGpuSyncTimeNs) {
        this.cpuGpuSyncTimeNs = (this.cpuGpuSyncTimeNs + cpuGpuSyncTimeNs)/2;
    }

    public long getCpuGpuSyncTimeNs() {
        return cpuGpuSyncTimeNs;
    }

    @Override
    public Runnable getDrawCommand() {
        return drawRunnable;
    }

    @Override
    public AtomicInteger getAtomicCounter() {
        return drawCounter;
    }
}
