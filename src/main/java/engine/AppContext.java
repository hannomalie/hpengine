package engine;

import camera.Camera;
import com.alee.laf.WebLookAndFeel;
import com.google.common.eventbus.Subscribe;
import component.InputControllerComponent;
import config.Config;
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
import org.lwjgl.util.vector.Vector4f;
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
import scene.EnvironmentProbe;
import scene.EnvironmentProbeFactory;
import scene.Scene;
import texture.Texture;
import util.gui.DebugFrame;
import util.script.ScriptManager;
import util.stopwatch.OpenGLStopWatch;
import util.stopwatch.StopWatch;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static log.ConsoleLogger.getLogger;

public class AppContext implements Extractor<RenderExtract> {

    private static volatile AppContext instance = null;

    private TimeStepThread thread;
    private volatile boolean frameFinished = true;
    private DrawResult latestDrawResult = null;
    private String latestGPUProfilingResult = "";
    private volatile boolean directionalLightNeedsShadowMapRedraw;
    private volatile boolean sceneInitiallyDrawn;
    private RenderExtract currentRenderExtract = new RenderExtract();
    private RenderExtract nextExtract = new RenderExtract();

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

    private static Logger LOGGER = getLogger();
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

        if (debug) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    WebLookAndFeel.install();
                    new DebugFrame();
                }
            }).start();
        }

        init();
        if (sceneName != null) {
            Renderer.getInstance();
            Scene scene = Scene.read(sceneName);
            AppContext.getInstance().setScene(scene);
        }
//        else {
//            AppContext.getInstance().getScene().addAll(AppContext.getInstance().loadTestScene());
//        }

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
        initOpenGLContext();
        EntityFactory.create();
        Renderer.init(DeferredRenderer.class);
        EntityFactory.init();
//        MaterialFactory.getInstance().initDefaultMaterials();

        glWatch = new OpenGLStopWatch();
        scriptManager = ScriptManager.getInstance();
        physicsFactory = new PhysicsFactory(this);
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
                                     if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
                                         turbo = 3f;
                                     }

                                     float rotationAmount = 1.1f * turbo * rotationDelta * seconds * Config.CAMERA_SPEED;
                                     if (Mouse.isButtonDown(0)) {
                                         getEntity().rotate(Transform.WORLD_UP, -Mouse.getDX() * rotationAmount);
                                     }
                                     if (Mouse.isButtonDown(1)) {
                                         getEntity().rotate(Transform.WORLD_RIGHT, Mouse.getDY() * rotationAmount);
                                     }
                                     if (Mouse.isButtonDown(2)) {
                                         getEntity().rotate(Transform.WORLD_VIEW, Mouse.getDX() * rotationAmount);
                                     }

                                     float moveAmount = turbo * posDelta * seconds * Config.CAMERA_SPEED;
                                     if (Keyboard.isKeyDown(Keyboard.KEY_W)) {
                                         getEntity().move(new Vector3f(0, 0, -moveAmount));
                                     }
                                     if (Keyboard.isKeyDown(Keyboard.KEY_A)) {
                                         getEntity().move(new Vector3f(-moveAmount, 0, 0));
                                     }
                                     if (Keyboard.isKeyDown(Keyboard.KEY_S)) {
                                         getEntity().move(new Vector3f(0, 0, moveAmount));
                                     }
                                     if (Keyboard.isKeyDown(Keyboard.KEY_D)) {
                                         getEntity().move(new Vector3f(moveAmount, 0, 0));
                                     }
                                     if (Keyboard.isKeyDown(Keyboard.KEY_Q)) {
                                         getEntity().move(new Vector3f(0, -moveAmount, 0));
                                     }
                                     if (Keyboard.isKeyDown(Keyboard.KEY_E)) {
                                         getEntity().move(new Vector3f(0, moveAmount, 0));
                                     }
                                 }
                             }
                );
        camera.init();
        camera.setPosition(new Vector3f(0, 20, 0));
        activeCamera = camera;
        // TODO: Check if this is still necessary
        activeCamera.rotateWorld(new Vector4f(0, 1, 0, 0.01f));
        activeCamera.rotateWorld(new Vector4f(1, 0, 0, 0.01f));
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

        thread = new TimeStepThread("World Main", 0.005f) {
            @Override
            public void update(float seconds) {
                self.update(seconds);
            }
        };
        thread.start();
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
        System.out.println("Finalize renderer");
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
            Model skyBox = new OBJLoader().loadTexturedModel(new File(AppContext.WORKDIR_NAME + "/assets/models/skybox.obj")).get(0);
            Entity skyBoxEntity = EntityFactory.getInstance().getEntity(new Vector3f(), skyBox);
            skyBoxEntity.setScale(100);
            entities.add(skyBoxEntity);

            Model sphere = new OBJLoader().loadTexturedModel(new File(AppContext.WORKDIR_NAME + "/assets/models/sphere.obj")).get(0);

            for (int i = 0; i < entityCount; i++) {
                for (int j = 0; j < entityCount; j++) {
                    for (int k = 0; k < entityCount; k++) {

                        MaterialFactory.MaterialInfo materialInfo = new MaterialFactory.MaterialInfo().setName("Default" + i + "_" + j + "_" + k)
                                .setDiffuse(new Vector3f(1, 1, 1))
                                .setRoughness((float) i / entityCount)
                                .setMetallic((float) j / entityCount)
                                .setDiffuse(new Vector3f((float) k / entityCount, 0, 0));
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
//							PhysicsComponent physicsComponent = physicsFactory.addBallPhysicsComponent(entity);
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

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return entities;
        }
    }


    private boolean STRG_PRESSED_LAST_FRAME = false;

    private void update(float seconds) {

        StopWatch.getInstance().start("Controls update");

//        if(Keyboard.isCreated())
        {
            if (PICKING_CLICK == 0 && Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) && Display.isActive()) {
                if (Mouse.isButtonDown(0) && !STRG_PRESSED_LAST_FRAME) {
                    PICKING_CLICK = 1;
                    STRG_PRESSED_LAST_FRAME = true;
                } else if (Mouse.isButtonDown(1) && !STRG_PRESSED_LAST_FRAME) {
                    getScene().getEntities().parallelStream().forEach(e -> {
                        e.setSelected(false);
                    });
                }
            } else {
                STRG_PRESSED_LAST_FRAME = false;
            }

            if(Keyboard.isKeyDown(Keyboard.KEY_0)) {
                Config.MODEL_LOD_STRATEGY = ModelLod.ModelLodStrategy.CONSTANT_LEVEL;
                System.out.println("Model lod 0");
            } else if(Keyboard.isKeyDown(Keyboard.KEY_1)) {
                Config.MODEL_LOD_STRATEGY = ModelLod.ModelLodStrategy.CONSTANT_LEVEL_1;
                System.out.println("Model lod 1");
            } else if(Keyboard.isKeyDown(Keyboard.KEY_2)) {
                Config.MODEL_LOD_STRATEGY = ModelLod.ModelLodStrategy.CONSTANT_LEVEL_2;
                System.out.println("Model lod 2");
            } else if(Keyboard.isKeyDown(Keyboard.KEY_3)) {
                Config.MODEL_LOD_STRATEGY = ModelLod.ModelLodStrategy.CONSTANT_LEVEL_3;
                System.out.println("Model lod 3");
            } else if(Keyboard.isKeyDown(Keyboard.KEY_4)) {
                Config.MODEL_LOD_STRATEGY = ModelLod.ModelLodStrategy.CONSTANT_LEVEL_4;
                System.out.println("Model lod 4");
            } else if(Keyboard.isKeyDown(Keyboard.KEY_5)) {
                Config.MODEL_LOD_STRATEGY = ModelLod.ModelLodStrategy.CONSTANT_LEVEL_5;
                System.out.println("Model lod 5");
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

        boolean anyPointLightHasMoved = scene.getPointLights().stream()
                        .filter(light -> light.hasMoved()).collect(Collectors.toList())
                        .isEmpty();
        if(scene.getEntities().parallelStream().anyMatch(entity -> entity.hasMoved())) {
            if(getScene() != null) { getScene().calculateMinMax(); }
            entityHasMoved = true;
        }


        if (Renderer.getInstance().isFrameFinished()) {
            OpenGLContext.getInstance().blockUntilEmpty();
            if((entityHasMoved || entityAdded) && scene != null) {
                EntityFactory.getInstance().bufferEntities(); entityAdded = false;
            }

            RenderExtract currentExtract = extract(directionalLight, anyPointLightHasMoved, getActiveCamera(), latestDrawResult);

            OpenGLContext.getInstance().execute(() -> {
                Renderer.getInstance().startFrame();

                latestDrawResult = Renderer.getInstance().draw(currentExtract);
                latestGPUProfilingResult = Renderer.getInstance().endFrame();
                resetState(currentExtract);
                AppContext.getEventBus().post(new FrameFinishedEvent(latestDrawResult, latestGPUProfilingResult));
            }, false);
        }

        scene.endFrame(activeCamera);
    }

    @Override
    public void resetState(RenderExtract currentExtract) {
        entityHasMoved = currentExtract.anEntityHasMoved ? false : entityHasMoved;
        directionalLightNeedsShadowMapRedraw = currentExtract.directionalLightNeedsShadowMapRender && latestDrawResult.directionalLightShadowMapWasRendered() ? false : directionalLightNeedsShadowMapRedraw;
        sceneInitiallyDrawn = (!currentExtract.sceneInitiallyDrawn && !latestDrawResult.notYetUploadedVertexBufferDrawn() ? true : sceneInitiallyDrawn);
    }

    @Override
    public RenderExtract extract(DirectionalLight directionalLight, boolean anyPointLightHasMoved, Camera extractedCamera, DrawResult latestDrawResult) {
        return new RenderExtract().init(extractedCamera, scene.getEntities(), directionalLight, entityHasMoved, directionalLightNeedsShadowMapRedraw,anyPointLightHasMoved, (sceneInitiallyDrawn && !Config.forceRevoxelization), scene.getMinMax()[0], scene.getMinMax()[1], latestDrawResult);
    }

    private JFrame frame;
    private void initOpenGLContext() {
        try {
            OpenGLContext.getInstance();
            if(Config.isHeadless()) {
                Canvas canvas = new Canvas();
                frame = new JFrame("hpengine");
                frame.setSize(new Dimension(Config.WIDTH, Config.HEIGHT));
                JLayeredPane layeredPane = new JLayeredPane();
                canvas.setPreferredSize(new Dimension(Config.WIDTH, Config.HEIGHT));
                layeredPane.add(canvas, 0);
                //            JPanel overlayPanel = new JPanel();
                //            overlayPanel.setOpaque(true);
                //            overlayPanel.add(new JButton("asdasdasd"));
                //			layeredPane.add(overlayPanel, 1);
                //            frame.add(layeredPane);
                //			frame.setLayout(new BorderLayout());
                frame.getContentPane().add(new JButton("adasd"), BorderLayout.PAGE_START);
                frame.getContentPane().add(new JButton("xxx"), BorderLayout.PAGE_END);
                frame.getContentPane().add(canvas, BorderLayout.CENTER);
                frame.pack();
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setVisible(false);
                Display.setParent(canvas);
            }
//            OpenGLContext.getInstance().attach(canvas);

        } catch (LWJGLException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
    private volatile boolean entityHasMoved = false;

    public Scene getScene() {
        return scene;
    }

    public void setScene(Scene scene) {
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

    private void switchExtracts() {
        synchronized (currentRenderExtract) {
            synchronized (nextExtract) {
                RenderExtract temp = currentRenderExtract;
                currentRenderExtract = nextExtract;
                nextExtract = temp;
            }
        }
    }
}
