package engine;

import camera.Camera;
import com.alee.laf.WebLookAndFeel;
import com.google.common.eventbus.Subscribe;
import component.InputControllerComponent;
import component.JavaComponent;
import component.ModelComponent;
import component.PhysicsComponent;
import config.Config;
import engine.input.Input;
import engine.model.Entity;
import engine.model.EntityFactory;
import engine.model.Model;
import engine.model.OBJLoader;
import event.*;
import event.bus.EventBus;
import net.engio.mbassy.listener.Handler;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector3f;
import physic.PhysicsFactory;
import renderer.DeferredRenderer;
import renderer.OpenGLContext;
import renderer.RenderExtract;
import renderer.Renderer;
import renderer.drawstrategy.DrawResult;
import renderer.drawstrategy.GBuffer;
import renderer.fps.FPSCounter;
import renderer.light.DirectionalLight;
import renderer.light.LightFactory;
import renderer.light.PointLight;
import renderer.lodstrategy.ModelLod;
import renderer.material.Material;
import renderer.material.MaterialFactory;
import renderer.material.MaterialInfo;
import scene.EnvironmentProbe;
import scene.EnvironmentProbeFactory;
import scene.Scene;
import shader.Program;
import shader.ProgramFactory;
import texture.Texture;
import util.gui.DebugFrame;
import util.script.ScriptManager;
import util.stopwatch.OpenGLStopWatch;
import util.stopwatch.StopWatch;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class AppContext implements Extractor<RenderExtract> {
    public static int WINDOW_WIDTH = Config.WIDTH;
    public static int WINDOW_HEIGHT = Config.HEIGHT;

    private static volatile AppContext instance = null;


    private final ExecutorService pool = Executors.newFixedThreadPool(1);
    private final CountingCompletionService<PerEntityInfo> completionService = new CountingCompletionService<>(pool);

    private FpsCountedTimeStepThread thread;
    private DrawResult latestDrawResult = null;
    private String latestGPUProfilingResult = "";
    private volatile boolean directionalLightNeedsShadowMapRedraw;
    private volatile boolean sceneInitiallyDrawn;
    private RenderExtract currentRenderExtract = new RenderExtract();
    private RenderExtract nextExtract = new RenderExtract();
    private boolean MOUSE_LEFT_PRESSED_LAST_FRAME;
    private volatile RenderExtract currentExtract;
    private final FPSCounter updateFpsCounter = new FPSCounter();
    public static boolean MULTITHREADED_RENDERING = true;

    public static AppContext getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Call AppContext.init() before using it");
        }
        return instance;
    }

    public static final String WORKDIR_NAME = "hp";
    public static final String ASSETDIR_NAME = "hp/assets";
    ScriptManager scriptManager;
    PhysicsFactory physicsFactory;
    Scene scene;
    private int entityCount = 3;
    public volatile int PICKING_CLICK = 0;
    private Camera camera;
    private Camera activeCamera;

    private static final Logger LOGGER = Logger.getLogger(AppContext.class.getName());
    private volatile boolean initialized;

    private OpenGLStopWatch glWatch;

    public static void main(String[] args) {

        String sceneName = null;
        boolean debug = true;
        for (String string : args) {
            if ("debug=false".equals(string)) {
                debug = false;
            } else if ("secondpassscale=0.5".equals(string)) {
                GBuffer.SECONDPASSSCALE = 0.5f;
            } else if ("fullhd".equals(string)) {
                Config.WIDTH = 1920;
                Config.HEIGHT = 1080;
            } else {
                sceneName = string;
                break;
            }
        }

        try {
            EventQueue.invokeAndWait(() -> WebLookAndFeel.install());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        init();
        if (debug) {
            new DebugFrame();
        }
        if (sceneName != null) {
            Renderer.getInstance();
            Scene scene = Scene.read(sceneName);
            AppContext.getInstance().setScene(scene);
        }

        try {
            JavaComponent initScript = new JavaComponent(new String(Files.readAllBytes(FileSystems.getDefault().getPath(AppContext.WORKDIR_NAME + "/assets/scripts/Init.java"))));
            initScript.init();
            System.out.println("initScript = " + initScript.isInitialized());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void init() {
        if (instance != null) {
            return;
        }
        init(false);
    }

    public static void init(boolean headless) {
        instance = new AppContext();
        instance.initialize(headless);
        instance.simulate();
    }

    private AppContext() {
    }

    private void initialize(boolean headless) {
        getEventBus().register(this);
        Config.setHeadless(headless);
        initWorkDir();
        frame = new JFrame("hpengine");
        frame.setSize(new Dimension(Config.WIDTH, Config.HEIGHT));
        JLayeredPane layeredPane = new JLayeredPane();
        canvas = new Canvas() {
            @Override
            public void addNotify() {
                super.addNotify();
            }

            @Override
            public void removeNotify() {
                super.removeNotify();
            }
        };
        canvas.setPreferredSize(new Dimension(Config.WIDTH, Config.HEIGHT));
        canvas.setIgnoreRepaint(true);
        layeredPane.add(canvas, 0);
//        JPanel overlayPanel = new JPanel();
//        overlayPanel.setOpaque(true);
//        overlayPanel.add(new JButton("asdasdasd"));
//        layeredPane.add(overlayPanel, 1);
        frame.add(layeredPane);
        frame.setLayout(new BorderLayout());
//        frame.getContentPane().add(new JButton("adasd"), BorderLayout.PAGE_START);
//        frame.getContentPane().add(new JButton("xxx"), BorderLayout.PAGE_END);
        frame.getContentPane().add(canvas, BorderLayout.CENTER);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        try {
            Display.setParent(canvas);
        } catch (LWJGLException e) {
            e.printStackTrace();
        }
        frame.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent evt) {
                AppContext.WINDOW_WIDTH = frame.getWidth();
                AppContext.WINDOW_HEIGHT = frame.getHeight();
            }
        });
        initOpenGLContext();

        EntityFactory.create();
        Renderer.init(DeferredRenderer.class);
//        Renderer.init(SimpleTextureRenderer.class);
        EntityFactory.init();
//        MaterialFactory.getInstance().initDefaultMaterials();

        glWatch = new OpenGLStopWatch();
        scriptManager = ScriptManager.getInstance();
        physicsFactory = new PhysicsFactory();
        ScriptManager.getInstance().defineGlobals();

        scene = new Scene();
        AppContext.getEventBus().register(scene);
        AppContext self = this;
        OpenGLContext.getInstance().execute(() -> {
            scene.init();
        }, true);
        float rotationDelta = 125f;
        float scaleDelta = 0.1f;
        float posDelta = 100f;
        camera = (Camera) new Camera().
                addComponent(new InputControllerComponent() {
                                 private static final long serialVersionUID = 1L;

                                 @Override
                                 public void update(float seconds) {

//                                     if(!Keyboard.isCreated()) {
//                                         return;
//                                     }

                                     float turbo = 1f;
                                     if (Input.isKeyPressed(Keyboard.KEY_LSHIFT)) {
                                         turbo = 3f;
                                     }

                                     float rotationAmount = 1.1f * turbo * rotationDelta * seconds * Config.CAMERA_SPEED;
                                     if (Input.isMouseClicked(0)) {
                                         getEntity().rotate(Transform.WORLD_UP, -Mouse.getDX() * rotationAmount);
                                     }
                                     if (Input.isMouseClicked(1)) {
                                         getEntity().rotate(Transform.WORLD_RIGHT, Mouse.getDY() * rotationAmount);
                                     }
                                     if (Input.isMouseClicked(2)) {
                                         getEntity().rotate(Transform.WORLD_VIEW, Mouse.getDX() * rotationAmount);
                                     }

                                     float moveAmount = turbo * posDelta * seconds * Config.CAMERA_SPEED;
                                     if (Input.isKeyPressed(Keyboard.KEY_W)) {
                                         getEntity().move(new Vector3f(0, 0, -moveAmount));
                                     }
                                     if (Input.isKeyPressed(Keyboard.KEY_A)) {
                                         getEntity().move(new Vector3f(-moveAmount, 0, 0));
                                     }
                                     if (Input.isKeyPressed(Keyboard.KEY_S)) {
                                         getEntity().move(new Vector3f(0, 0, moveAmount));
                                     }
                                     if (Input.isKeyPressed(Keyboard.KEY_D)) {
                                         getEntity().move(new Vector3f(moveAmount, 0, 0));
                                     }
                                     if (Input.isKeyPressed(Keyboard.KEY_Q)) {
                                         getEntity().move(new Vector3f(0, -moveAmount, 0));
                                     }
                                     if (Input.isKeyPressed(Keyboard.KEY_E)) {
                                         getEntity().move(new Vector3f(0, moveAmount, 0));
                                     }
                                 }
                             }
                );
        camera.init();
        camera.setPosition(new Vector3f(0, 20, 0));
        activeCamera = camera;
        initialized = true;
        getEventBus().post(new AppContextInitializedEvent());
    }

    private void initWorkDir() {
        ArrayList<File> dirs = new ArrayList<>();
        dirs.add(new File(WORKDIR_NAME));
        dirs.add(new File(ASSETDIR_NAME));
        dirs.add(new File(Texture.getDirectory()));
        dirs.add(new File(Material.getDirectory()));
        dirs.add(new File(Entity.getDirectory()));
        dirs.add(new File(Scene.getDirectory()));

        for (File file : dirs) {
            createIfAbsent(file);
        }
    }

    private boolean createIfAbsent(File folder) {

        if (!folder.exists()) {
            return folder.mkdir();
        }
        return true;
    }

    public void simulate() {

        AppContext self = this;

        thread = new WorldMainThread("World Main", 0.005f);
        thread.start();

        new RenderThread("Render").start();
    }

    private static class WorldMainThread extends FpsCountedTimeStepThread {

        public WorldMainThread(String name, float minCycleTimeInS) { super(name, minCycleTimeInS); }
        @Override
        public void update(float seconds) {
            AppContext.getInstance().update(seconds);
        }
    }
    private static class RenderThread extends TimeStepThread {

        public RenderThread(String name) { super(name); }
        @Override
        public void update(float seconds) {
            if(MULTITHREADED_RENDERING) {
                try {
                    AppContext.getInstance().actuallyDraw(AppContext.getInstance().getScene().getDirectionalLight());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private void destroyOpenGL() {
        OpenGLContext.getInstance().getDrawThread().stopRequested = true;
        try {
            Display.destroy();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public void destroy() {
        LOGGER.info("Finalize renderer");
        destroyOpenGL();
        if(frame != null) {
            frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
        }
        System.exit(0);
    }

    public List<Entity> loadTestScene() {
        List<Entity> entities = new ArrayList<>();

        OpenGLContext.exitOnGLError("loadTestScene");

        try {
//            Model skyBox = new OBJLoader().loadTexturedModel(new File(AppContext.WORKDIR_NAME + "/assets/models/skybox.obj")).get(0);
//            Entity skyBoxEntity = EntityFactory.getInstance().getEntity(new Vector3f(), skyBox);
//            skyBoxEntity.setScale(100);
//            entities.add(skyBoxEntity);

            Model sphere = new OBJLoader().loadTexturedModel(new File(AppContext.WORKDIR_NAME + "/assets/models/sphere.obj")).get(0);

            for (int i = 0; i < entityCount; i++) {
                for (int j = 0; j < entityCount; j++) {
                    for (int k = 0; k < entityCount; k++) {

                        MaterialInfo materialInfo = new MaterialInfo().setName("Default" + i + "_" + j + "_" + k)
                                .setDiffuse(new Vector3f(1, 1, 1))
                                .setRoughness((float) i / entityCount)
                                .setMetallic((float) j / entityCount)
                                .setDiffuse(new Vector3f((float) k / entityCount, 0, 0))
                                .setAmbient(1);
                        Material mat = MaterialFactory.getInstance().getMaterial(materialInfo.setName("Default_" + i + "_" + j));
                        mat.setDiffuse(new Vector3f((float)i/entityCount, 0,0));
                        mat.setMetallic((float)j/entityCount);
                        mat.setRoughness((float)k/entityCount);

                        try {
                            Vector3f position = new Vector3f(i * 20, k * 10, -j * 20);
                            Entity entity = EntityFactory.getInstance().getEntity(position, "Entity_" + System.currentTimeMillis(), sphere, mat);
                            PointLight pointLight = LightFactory.getInstance().getPointLight(10);
                            pointLight.setPosition(new Vector3f(i * 19, k * 15, -j * 19));
                            scene.addPointLight(pointLight);
//							Vector3f scale = new Vector3f(0.5f, 0.5f, 0.5f);
//							scale.scale(new Random().nextFloat()*14);
//							entity.setScale(scale);
//
							PhysicsComponent physicsComponent = physicsFactory.addBallPhysicsComponent(entity);
                            entity.addComponent(physicsComponent);
//							physicsComponent.getRigidBody().applyCentralImpulse(new javax.vecmath.Vector3f(10*new Random().nextFloat(), 10*new Random().nextFloat(), 10*new Random().nextFloat()));
//							physicsComponent.getRigidBody().applyTorqueImpulse(new javax.vecmath.Vector3f(0, 100*new Random().nextFloat(), 0));

                            entities.add(entity);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

//			StopWatch.getInstance().start("Load Sponza");
//			List<Model> sponza = renderer.getOBJLoader().loadTexturedModel(new File(AppContext.WORKDIR_NAME + "/assets/models/sponza.obj"));
//			for (Model model : sponza) {
////				model.setMaterial(mirror);
////				if(model.getMaterial().getName().contains("fabric")) {
////					model.setMaterial(mirror);
////				}
//				Entity entity = getEntityFactory().getEntity(new Vector3f(0,-21f,0), model);
////				physicsFactory.addMeshPhysicsComponent(entity, 0);
//				Vector3f scale = new Vector3f(3.1f, 3.1f, 3.1f);
//				entity.setScale(scale);
//				entities.add(entity);
//			}
//			List<Model> skyBox = renderer.getOBJLoader().loadTexturedModel(new File(AppContext.WORKDIR_NAME + "/assets/models/skybox.obj"));
//			for (Model model : skyBox) {
//				Entity entity = getEntityFactory().getEntity(new Vector3f(0,0,0), model.getName(), model, renderer.getMaterialFactory().get("mirror"));
//				Vector3f scale = new Vector3f(3000, 3000f, 3000f);
//				entity.setScale(scale);
//				entities.add(entity);
//			}
//			StopWatch.getInstance().stopAndPrintMS();

            for(Entity entity : entities) {
                entity.init();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return entities;
        }
    }


    private boolean STRG_PRESSED_LAST_FRAME = false;

    private void update(float seconds) {
        SwingUtilities.invokeLater(() -> {
            //TODO Don't use Display title anymore
            frame.setTitle(Display.getTitle());
        });
        StopWatch.getInstance().start("Controls update");
        if(Input.isMouseClicked(0)) {
            if(!MOUSE_LEFT_PRESSED_LAST_FRAME) {
                AppContext.getEventBus().post(new ClickEvent());
            }
            MOUSE_LEFT_PRESSED_LAST_FRAME = true;
        } else {
            MOUSE_LEFT_PRESSED_LAST_FRAME = false;
        }

//        if(Keyboard.isCreated())
        {
            if (PICKING_CLICK == 0 && Input.isKeyPressed(Keyboard.KEY_LCONTROL) && Display.isActive()) {
                if (Input.isMouseClicked(0) && !STRG_PRESSED_LAST_FRAME) {
                    PICKING_CLICK = 1;
                    STRG_PRESSED_LAST_FRAME = true;
                } else if (Input.isMouseClicked(1) && !STRG_PRESSED_LAST_FRAME) {
                    getScene().getEntities().parallelStream().forEach(e -> {
                        e.setSelected(false);
                    });
                }
            } else {
                STRG_PRESSED_LAST_FRAME = false;
            }

            if(Input.isKeyPressed(Keyboard.KEY_0)) {
                Config.MODEL_LOD_STRATEGY = ModelLod.ModelLodStrategy.CONSTANT_LEVEL;
                LOGGER.info("Model lod 0");
            } else if(Input.isKeyPressed(Keyboard.KEY_1)) {
                Config.MODEL_LOD_STRATEGY = ModelLod.ModelLodStrategy.CONSTANT_LEVEL_1;
                LOGGER.info("Model lod 1");
            } else if(Input.isKeyPressed(Keyboard.KEY_2)) {
                Config.MODEL_LOD_STRATEGY = ModelLod.ModelLodStrategy.CONSTANT_LEVEL_2;
                LOGGER.info("Model lod 2");
            } else if(Input.isKeyPressed(Keyboard.KEY_3)) {
                Config.MODEL_LOD_STRATEGY = ModelLod.ModelLodStrategy.CONSTANT_LEVEL_3;
                LOGGER.info("Model lod 3");
            } else if(Input.isKeyPressed(Keyboard.KEY_4)) {
                Config.MODEL_LOD_STRATEGY = ModelLod.ModelLodStrategy.CONSTANT_LEVEL_4;
                LOGGER.info("Model lod 4");
            } else if(Input.isKeyPressed(Keyboard.KEY_5)) {
                Config.MODEL_LOD_STRATEGY = ModelLod.ModelLodStrategy.CONSTANT_LEVEL_5;
                LOGGER.info("Model lod 5");
            }
        }

        DirectionalLight directionalLight = scene.getDirectionalLight();

        StopWatch.getInstance().stopAndPrintMS();
        StopWatch.getInstance().start("Camera update");
        camera.update(seconds);

        StopWatch.getInstance().stopAndPrintMS();
        StopWatch.getInstance().start("Light update");
        if (directionalLight.hasMoved()) {
            OpenGLContext.getInstance().execute(() -> {
                for (EnvironmentProbe probe : EnvironmentProbeFactory.getInstance().getProbes()) {
                    Renderer.getInstance().addRenderProbeCommand(probe, true);
                }
                EnvironmentProbeFactory.getInstance().draw(true);
            });
        }
        StopWatch.getInstance().stopAndPrintMS();

        StopWatch.getInstance().start("Entities update");
        scene.update(seconds);
        LightFactory.getInstance().update(seconds);
        StopWatch.getInstance().stopAndPrintMS();

        updateFpsCounter.update();
        if(!MULTITHREADED_RENDERING) {
            actuallyDraw(getScene().getDirectionalLight());
        }
    }

    protected void actuallyDraw(DirectionalLight directionalLight) {
        boolean anyPointLightHasMoved = false;
        for(int i = 0; i < scene.getPointLights().size(); i++) {
            if(!scene.getPointLights().get(i).hasMoved()) { continue; }
            anyPointLightHasMoved = true;
            break;
        }

        for(int i = 0; i < scene.getEntities().size(); i++) {
            if(!scene.getEntities().get(i).hasMoved()) { continue; }
            if(getScene() != null) { getScene().calculateMinMax(); }
            entityHasMoved = true;
            break;
        }

        if((entityHasMoved || entityAdded) && scene != null) {
            entityAdded = false;
            directionalLightNeedsShadowMapRedraw = true;
        }
        if(directionalLight.hasMoved()) {
            directionalLightNeedsShadowMapRedraw = true;
            scene.getDirectionalLight().setHasMoved(false);
        }

        List<PerEntityInfo> perEntityInfos = getPerEntityInfos(camera);
        if (Renderer.getInstance().isFrameFinished()) {
            currentExtract = extract(directionalLight, anyPointLightHasMoved, getActiveCamera(), latestDrawResult, perEntityInfos, entityHasMoved);

            if(entityHasMoved) {
                EntityFactory.getInstance().bufferEntities();
                entityHasMoved = false;
                for (Entity entity : scene.getEntities()) {
                    entity.setHasMoved(false);
                }
            }
//            long blockedMs = OpenGLContext.getInstance().blockUntilEmpty();
//            LOGGER.fine("Waited ms for renderer to complete: " + blockedMs);

            OpenGLContext.getInstance().execute(() -> {
                RenderExtract extractCopy = new RenderExtract(currentExtract);
                Renderer.getInstance().startFrame();
                latestDrawResult = Renderer.getInstance().draw(extractCopy);
                latestGPUProfilingResult = Renderer.getInstance().endFrame();
//                resetState(extractCopy);
                AppContext.getEventBus().post(new FrameFinishedEvent(latestDrawResult, latestGPUProfilingResult));

                if(scene != null) {
                    scene.endFrame();
                }

                Display.setTitle(String.format("Render %03.0f fps | %03.0f ms - Update %03.0f fps | %03.0f ms",
                        Renderer.getInstance().getCurrentFPS(), Renderer.getInstance().getMsPerFrame(), AppContext.getInstance().getFPSCounter().getFPS(), AppContext.getInstance().getFPSCounter().getMsPerFrame()));
            }, true);
            resetState();

        }

    }

    private Vector3f tempDistVector = new Vector3f();
    Map<ModelComponent, PerEntityInfo> cash0 = new HashMap<>();
    public List<PerEntityInfo> getPerEntityInfos(Camera camera) {
        Vector3f cameraWorldPosition = camera.getWorldPosition();

        Program firstpassDefaultProgram = ProgramFactory.getInstance().getFirstpassDefaultProgram();

        List<ModelComponent> modelComponents = AppContext.getInstance().getScene().getModelComponents();
        List<PerEntityInfo> currentPerEntityInfos = new ArrayList<>(modelComponents.size());
        currentPerEntityInfos.clear();
        for (ModelComponent modelComponent : modelComponents) {
            // TODO: Implement strategy pattern

            Entity entity = modelComponent.getEntity();
            Vector3f centerWorld = entity.getCenterWorld();
            Vector3f.sub(cameraWorldPosition, centerWorld, tempDistVector);
            float distanceToCamera = tempDistVector.length();
            boolean isInReachForTextureLoading = distanceToCamera < 50 || distanceToCamera < 2.5f * modelComponent.getBoundingSphereRadius();

            // TODO: Fix this
            boolean visibleForCamera = true;//entity.isInFrustum(camera) || entity.getInstanceCount() > 1; // TODO: Better culling for instances

            int entityIndexOf = AppContext.getInstance().getScene().getEntityIndexOf(entity);
            PerEntityInfo info = cash0.get(modelComponent);
            if(info != null) {
                info.init(null, firstpassDefaultProgram, entityIndexOf, entityIndexOf, entity.isVisible(), entity.isSelected(), Config.DRAWLINES_ENABLED, cameraWorldPosition, modelComponent.getMaterial(), isInReachForTextureLoading, entity.getInstanceCount(), visibleForCamera, entity.getUpdate(), entity.getMinMaxWorld()[0], entity.getMinMaxWorld()[1], modelComponent.getIndexCount(), modelComponent.getIndexOffset(), modelComponent.getBaseVertex());
            } else {
                info = new PerEntityInfo(null, firstpassDefaultProgram, entityIndexOf, entityIndexOf, entity.isVisible(), entity.isSelected(), Config.DRAWLINES_ENABLED, cameraWorldPosition, modelComponent.getMaterial(), isInReachForTextureLoading, entity.getInstanceCount(), visibleForCamera, entity.getUpdate(), entity.getMinMaxWorld()[0], entity.getMinMaxWorld()[1], modelComponent.getIndexCount(), modelComponent.getIndexOffset(), modelComponent.getBaseVertex());
                cash0.put(modelComponent, info);
            }

            currentPerEntityInfos.add(info);
        }

        List<PerEntityInfo> temp = currentPerEntityInfos;
        return temp;
    }

    @Override
    public void resetState() {
        entityHasMoved = false;
        directionalLightNeedsShadowMapRedraw = false;//(currentExtract.directionalLightNeedsShadowMapRender && latestDrawResult.directionalLightShadowMapWasRendered()) ? false : directionalLightNeedsShadowMapRedraw;
        sceneInitiallyDrawn = true;//(!currentExtract.sceneInitiallyDrawn ? true : sceneInitiallyDrawn);
    }

    @Override
    public RenderExtract extract(DirectionalLight directionalLight, boolean anyPointLightHasMoved, Camera extractedCamera, DrawResult latestDrawResult, List<PerEntityInfo> perEntityInfos, boolean entityHasMoved) {
        return new RenderExtract().init(extractedCamera, directionalLight, entityHasMoved, directionalLightNeedsShadowMapRedraw ,anyPointLightHasMoved, (sceneInitiallyDrawn && !Config.forceRevoxelization), scene.getMinMax()[0], scene.getMinMax()[1], latestDrawResult, perEntityInfos);
    }

    JFrame frame;
    Canvas canvas;
    private void initOpenGLContext() {
        OpenGLContext.getInstance();
    }
    private volatile boolean entityHasMoved = false;

    public Scene getScene() {
        return scene;
    }

    public void setScene(Scene scene) {
        physicsFactory.clearWorld();
        this.scene = scene;
        OpenGLContext.getInstance().execute(() -> {
            StopWatch.getInstance().start("Scene init");
            scene.init();
            StopWatch.getInstance().stopAndPrintMS();
        }, true);
        restoreWorldCamera();
    }

    public Camera getCamera() {
        return camera;
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
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
        return thread.getFpsCounter();
    }

    private volatile boolean entityAdded = false;
    @Subscribe
    @Handler
    public void handle(EntityAddedEvent e) {
        entityAdded = true;
        directionalLightNeedsShadowMapRedraw = true;
        sceneInitiallyDrawn = false;
    }

    @Subscribe
    @Handler
    public void handle(DirectionalLightHasMovedEvent e) {
        directionalLightNeedsShadowMapRedraw = true;
    }

    @Subscribe
    @Handler
    public void handle(SceneInitEvent e) {
        sceneInitiallyDrawn = false;
    }

//    TODO: Use this
    private void switchExtracts() {
        synchronized (currentRenderExtract) {
            synchronized (nextExtract) {
                RenderExtract temp = currentRenderExtract;
                currentRenderExtract = nextExtract;
                nextExtract = temp;
            }
        }
    }
    public PhysicsFactory getPhysicsFactory() {
        return physicsFactory;
    }

    public JFrame getFrame() {
        return frame;
    }

    public FPSCounter getUpdateFpsCounter() {
        return updateFpsCounter;
    }
}
