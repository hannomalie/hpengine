package de.hanno.hpengine.engine;

import com.alee.laf.WebLookAndFeel;
import com.google.common.eventbus.Subscribe;
import de.hanno.hpengine.camera.Camera;
import de.hanno.hpengine.camera.MovableCamera;
import de.hanno.hpengine.component.JavaComponent;
import de.hanno.hpengine.component.ModelComponent;
import de.hanno.hpengine.component.PhysicsComponent;
import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.engine.input.Input;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.EntityFactory;
import de.hanno.hpengine.engine.model.Model;
import de.hanno.hpengine.engine.model.OBJLoader;
import de.hanno.hpengine.event.*;
import de.hanno.hpengine.event.bus.EventBus;
import de.hanno.hpengine.physic.PhysicsFactory;
import de.hanno.hpengine.renderer.DeferredRenderer;
import de.hanno.hpengine.renderer.OpenGLContext;
import de.hanno.hpengine.renderer.RenderState;
import de.hanno.hpengine.renderer.Renderer;
import de.hanno.hpengine.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.renderer.drawstrategy.SecondPassResult;
import de.hanno.hpengine.renderer.fps.FPSCounter;
import de.hanno.hpengine.renderer.light.LightFactory;
import de.hanno.hpengine.renderer.light.PointLight;
import de.hanno.hpengine.renderer.material.Material;
import de.hanno.hpengine.renderer.material.MaterialFactory;
import de.hanno.hpengine.renderer.material.MaterialInfo;
import de.hanno.hpengine.scene.Scene;
import de.hanno.hpengine.shader.Program;
import de.hanno.hpengine.shader.ProgramFactory;
import de.hanno.hpengine.texture.Texture;
import de.hanno.hpengine.util.gui.DebugFrame;
import de.hanno.hpengine.util.multithreading.DoubleBuffer;
import de.hanno.hpengine.util.script.ScriptManager;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import de.hanno.hpengine.util.stopwatch.OpenGLStopWatch;
import de.hanno.hpengine.util.stopwatch.StopWatch;
import net.engio.mbassy.listener.Handler;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector3f;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Engine {

    private static volatile Engine instance = null;

    private UpdateThread updateThread;
    private RenderThread renderThread;

    private final DrawResult latestDrawResult = new DrawResult(new FirstPassResult(), new SecondPassResult());
    private volatile String latestGPUProfilingResult = "";
    private volatile boolean directionalLightNeedsShadowMapRedraw;
    private DoubleBuffer<RenderState> renderState;

    public static Engine getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Call Engine.init() before using it");
        }
        return instance;
    }

    public static final String WORKDIR_NAME = "hp";
    public static final String ASSETDIR_NAME = "hp/assets";
    ScriptManager scriptManager;
    PhysicsFactory physicsFactory;
    Scene scene;
    private int entityCount = 3;
    private final Camera camera = new MovableCamera();
    private Camera activeCamera;

    private static final Logger LOGGER = Logger.getLogger(Engine.class.getName());
    private volatile boolean initialized;

    private OpenGLStopWatch glWatch;

    public static void main(String[] args) {

        String sceneName = null;
        boolean debug = true;
        for (String string : args) {
            if ("debug=false".equals(string)) {
                debug = false;
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
            Engine.getInstance().setScene(scene);
        }

        try {
            JavaComponent initScript = new JavaComponent(new String(Files.readAllBytes(FileSystems.getDefault().getPath(Engine.WORKDIR_NAME + "/assets/scripts/Init.java"))));
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
        instance = new Engine();
        instance.initialize();
    }

    private Engine() {
    }

    private void initialize() {
        getEventBus().register(this);
        initWorkDir();
        new ApplicationFrame();
        initOpenGLContext();

        EntityFactory.create();
        Renderer.init(DeferredRenderer.class);
//        Renderer.init(SimpleTextureRenderer.class);
//        MaterialFactory.getInstance().initDefaultMaterials();

        glWatch = new OpenGLStopWatch();
        scriptManager = ScriptManager.getInstance();
        physicsFactory = new PhysicsFactory();
        ScriptManager.getInstance().defineGlobals();

        renderState = new DoubleBuffer(new RenderState(), new RenderState());
        camera.init();
        camera.setPosition(new Vector3f(0, 20, 0));
        activeCamera = camera;
        scene = new Scene();
        scene.init();
        instance.startSimulation();
        initialized = true;
        getEventBus().post(new EngineInitializedEvent());
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

    public void startSimulation() {

        updateThread = new UpdateThread("Update", 0.033f);
        updateThread.start();

        renderThread = new RenderThread("Render");
        renderThread.start();
    }

    private void update(float seconds) {
        camera.update(seconds);
        scene.update(seconds);
        updateRenderState();
        if(!Config.MULTITHREADED_RENDERING) {
            actuallyDraw();
        }
    }

    private void updateRenderState() {
        boolean anyPointLightHasMoved = false;
        for(int i = 0; i < scene.getPointLights().size(); i++) {
            PointLight pointLight = scene.getPointLights().get(i);
            if(!pointLight.hasMoved()) { continue; }
            anyPointLightHasMoved = true;
            pointLight.setHasMoved(false);
        }

        boolean entityHasMoved = false;
        for(int i = 0; i < scene.getEntities().size(); i++) {
            if(!scene.getEntities().get(i).hasMoved()) { continue; }
            if(getScene() != null) { getScene().calculateMinMax(); }
            entityHasMoved = true;
            scene.getEntities().get(i).setHasMoved(false);
        }

        if((entityHasMoved || entityAdded) && scene != null) {
            entityAdded = false;
            directionalLightNeedsShadowMapRedraw = true;
        }
        if(scene.getDirectionalLight().hasMoved()) {
            directionalLightNeedsShadowMapRedraw = true;
            scene.getDirectionalLight().setHasMoved(false);
        }

        boolean finalAnyPointLightHasMoved = anyPointLightHasMoved;
        boolean finalEntityHasMoved = entityHasMoved;
        renderState.addCommand((renderStateX1) -> renderStateX1.init(scene.getVertexBuffer(), scene.getIndexBuffer(), getActiveCamera(), scene.getDirectionalLight(), finalEntityHasMoved, directionalLightNeedsShadowMapRedraw, finalAnyPointLightHasMoved, (scene.isInitiallyDrawn() && !Config.forceRevoxelization), scene.getMinMax()[0], scene.getMinMax()[1], latestDrawResult));
        addPerEntityInfos(renderState, camera);
        renderState.update();
    }

    protected void actuallyDraw() {
        OpenGLContext.getInstance().execute(() -> {
            Input.update();
            renderState.startRead();
            Renderer.getInstance().startFrame();
            latestDrawResult.reset();
            Renderer.getInstance().draw(latestDrawResult, renderState.getCurrentReadState());
            latestGPUProfilingResult = GPUProfiler.dumpTimings();
            Renderer.getInstance().endFrame();
            Engine.getEventBus().post(new FrameFinishedEvent(latestDrawResult, latestGPUProfilingResult));
            scene.endFrame();

            renderState.stopRead();
        }, true);
        resetState();
    }

    private Vector3f tempDistVector = new Vector3f();
    public void addPerEntityInfos(DoubleBuffer<RenderState> renderState, Camera camera) {
        Vector3f cameraWorldPosition = camera.getWorldPosition();

        Program firstpassDefaultProgram = ProgramFactory.getInstance().getFirstpassDefaultProgram();

        List<ModelComponent> modelComponents = Engine.getInstance().getScene().getModelComponents();

        for (ModelComponent modelComponent : modelComponents) {

            Entity entity = modelComponent.getEntity();
            Vector3f centerWorld = entity.getCenterWorld();
            Vector3f.sub(cameraWorldPosition, centerWorld, tempDistVector);
            float distanceToCamera = tempDistVector.length();
            boolean isInReachForTextureLoading = distanceToCamera < 50 || distanceToCamera < 2.5f * modelComponent.getBoundingSphereRadius();

            boolean visibleForCamera = entity.isInFrustum(camera) || entity.getInstanceCount() > 1; // TODO: Better culling for instances

            int entityIndexOf = Engine.getInstance().getScene().getEntityBufferIndex(entity);
            renderState.addCommandToCurrentWriteQueue((renderState1) -> {
                PerEntityInfo info = renderState1.cash.get(modelComponent);
                if(info == null) {
                    info = new PerEntityInfo(null, firstpassDefaultProgram, entityIndexOf, entity.isVisible(), entity.isSelected(), Config.DRAWLINES_ENABLED, cameraWorldPosition, modelComponent.getMaterial(), isInReachForTextureLoading, entity.getInstanceCount(), visibleForCamera, entity.getUpdate(), entity.getMinMaxWorld()[0], entity.getMinMaxWorld()[1], modelComponent.getIndexCount(), modelComponent.getIndexOffset(), modelComponent.getBaseVertex());
                } else {
                    info.init(null, firstpassDefaultProgram, entityIndexOf, entity.isVisible(), entity.isSelected(), Config.DRAWLINES_ENABLED, cameraWorldPosition, modelComponent.getMaterial(), isInReachForTextureLoading, entity.getInstanceCount(), visibleForCamera, entity.getUpdate(), entity.getMinMaxWorld()[0], entity.getMinMaxWorld()[1], modelComponent.getIndexCount(), modelComponent.getIndexOffset(), modelComponent.getBaseVertex());
                }
                renderState1.add(info);
            });
        }
    }

    public void resetState() {
        directionalLightNeedsShadowMapRedraw = false;
        scene.setInitiallyDrawn(true);
    }

    private void initOpenGLContext() {
        OpenGLContext.getInstance();
    }

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
        renderState.addCommand(renderState1 -> {
            renderState1.setIndexBuffer(scene.getIndexBuffer());
            renderState1.setVertexBuffer(scene.getVertexBuffer());
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

    private volatile boolean entityAdded = false;
    @Subscribe
    @Handler
    public void handle(EntityAddedEvent e) {
        entityAdded = true;
        directionalLightNeedsShadowMapRedraw = true;
        scene.setInitiallyDrawn(false);
        scene.setUpdateCache(true);
        renderState.addCommand((renderStateX -> {
            renderStateX.bufferEntites(scene.getEntities());
        }));
    }

    @Subscribe
    @Handler
    public void handle(DirectionalLightHasMovedEvent e) {
        directionalLightNeedsShadowMapRedraw = true;
    }

    @Subscribe
    @Handler
    public void handle(SceneInitEvent e) {
        scene.setInitiallyDrawn(false);
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

    public DoubleBuffer<RenderState> getRenderState() {
        return renderState;
    }

    private static class UpdateThread extends FpsCountedTimeStepThread {

        public UpdateThread(String name, float minCycleTimeInS) { super(name, minCycleTimeInS); }
        @Override
        public void update(float seconds) {
            Engine.getInstance().update(seconds > 0.001f ? seconds : 0.001f);
        }

        @Override
        public float getMinimumCycleTimeInSeconds() {
            return Config.LOCK_UPDATERATE ? minimumCycleTimeInSeconds : 0f;
        }
    }
    private static class RenderThread extends TimeStepThread {

        public RenderThread(String name) { super(name, 0.033f); }
        @Override
        public void update(float seconds) {
            if(Config.MULTITHREADED_RENDERING) {
                try {
                    Engine.getInstance().actuallyDraw();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public float getMinimumCycleTimeInSeconds() {
            return Config.LOCK_FPS ? minimumCycleTimeInSeconds : 0f;
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
        System.exit(0);
    }

    public List<Entity> loadTestScene() {
        List<Entity> entities = new ArrayList<>();

        OpenGLContext.exitOnGLError("loadTestScene");

        try {
//            Model skyBox = new OBJLoader().loadTexturedModel(new File(Engine.WORKDIR_NAME + "/assets/models/skybox.obj")).get(0);
//            Entity skyBoxEntity = EntityFactory.getInstance().getEntity(new Vector3f(), skyBox);
//            skyBoxEntity.setScale(100);
//            entities.add(skyBoxEntity);

            Model sphere = new OBJLoader().loadTexturedModel(new File(Engine.WORKDIR_NAME + "/assets/models/sphere.obj")).get(0);

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
//			List<Model> sponza = renderer.getOBJLoader().loadTexturedModel(new File(Engine.WORKDIR_NAME + "/assets/models/sponza.obj"));
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
//			List<Model> skyBox = renderer.getOBJLoader().loadTexturedModel(new File(Engine.WORKDIR_NAME + "/assets/models/skybox.obj"));
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
}
