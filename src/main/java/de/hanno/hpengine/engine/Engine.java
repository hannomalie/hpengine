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
import de.hanno.hpengine.engine.model.*;
import de.hanno.hpengine.event.*;
import de.hanno.hpengine.event.bus.EventBus;
import de.hanno.hpengine.physic.PhysicsFactory;
import de.hanno.hpengine.renderer.GraphicsContext;
import de.hanno.hpengine.renderer.Renderer;
import de.hanno.hpengine.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.renderer.drawstrategy.SecondPassResult;
import de.hanno.hpengine.renderer.fps.FPSCounter;
import de.hanno.hpengine.renderer.light.DirectionalLight;
import de.hanno.hpengine.renderer.light.LightFactory;
import de.hanno.hpengine.renderer.light.PointLight;
import de.hanno.hpengine.renderer.material.Material;
import de.hanno.hpengine.renderer.material.MaterialFactory;
import de.hanno.hpengine.renderer.material.MaterialInfo;
import de.hanno.hpengine.renderer.state.RenderState;
import de.hanno.hpengine.scene.Scene;
import de.hanno.hpengine.shader.Program;
import de.hanno.hpengine.shader.ProgramFactory;
import de.hanno.hpengine.texture.Texture;
import de.hanno.hpengine.texture.TextureFactory;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class Engine {

    private static volatile Engine instance = null;

    private UpdateThread updateThread;
    private RenderThread renderThread;

    private final DrawResult latestDrawResult = new DrawResult(new FirstPassResult(), new SecondPassResult());
    private volatile DoubleBuffer<RenderState> renderState;

    private volatile long entityMovedInCycle;
    private volatile long directionalLightMovedInCycle;
    private volatile boolean sceneIsInitiallyDrawn;
    private volatile AtomicLong cycle = new AtomicLong();
    private long pointLightMovedInCycle;

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
                Config.getInstance().setWidth(1920);
                Config.getInstance().setHeight(1080);
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
        ProgramFactory.init();
        TextureFactory.init();
        MaterialFactory.init();
        Renderer.init(Config.getInstance().getRendererClass());
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

    void update(float seconds) {
        camera.update(seconds);
        scene.update(seconds);
        updateRenderState();
        if(!Config.getInstance().isMultithreadedRendering()) {
            actuallyDraw();
        }
        sceneIsInitiallyDrawn = true;
    }

    private void updateRenderState() {
        boolean anyPointLightHasMoved = false;
        for(int i = 0; i < scene.getPointLights().size(); i++) {
            PointLight pointLight = scene.getPointLights().get(i);
            if(!pointLight.hasMoved()) { continue; }
            anyPointLightHasMoved = true;
            pointLight.setHasMoved(false);
        }

        boolean entityMovedThisCycle = false;
        for(int i = 0; i < scene.getEntities().size(); i++) {
            Entity entity = scene.getEntities().get(i);
            if(!entity.hasMoved()) { continue; }
            if(getScene() != null) { getScene().calculateMinMax(); }
            entityMovedInCycle = cycle.get();
            entity.setHasMovedInCycle(cycle.get());
            entity.setHasMoved(false);
            entityMovedThisCycle = true;
        }

        if((entityMovedThisCycle || entityAdded) && scene != null) {
            entityAdded = false;
        }
        DirectionalLight directionalLight = scene.getDirectionalLight();
        if(directionalLight.hasMoved()) {
            directionalLightMovedInCycle = cycle.get();
            directionalLight.setHasMoved(false);
        }

        if(anyPointLightHasMoved) {
            pointLightMovedInCycle = cycle.get();
        }
        if(entityMovedThisCycle) {
            renderState.addCommand((renderStateX1) -> {
                renderStateX1.bufferEntites(scene.getEntities());
            });
        }
        renderState.addCommand((renderStateX1) -> {
            Camera directionalLightCamera = scene.getDirectionalLight().getCamera();
            renderStateX1.init(scene.getVertexBuffer(), scene.getIndexBuffer(), getActiveCamera(), entityMovedInCycle, directionalLightMovedInCycle, pointLightMovedInCycle, sceneIsInitiallyDrawn, scene.getMinMax()[0], scene.getMinMax()[1], latestDrawResult, cycle.get(), directionalLightCamera.getViewMatrixAsBuffer(), directionalLightCamera.getProjectionMatrixAsBuffer(), directionalLightCamera.getViewProjectionMatrixAsBuffer(), directionalLight.getScatterFactor(), directionalLight.getDirection(), directionalLight.getColor());
            addPerEntityInfos(renderState, this.camera);
        });
        renderState.update();
    }

    Callable drawCallable = new Callable() {
        @Override
        public Object call() throws Exception {
            cycle.getAndIncrement();
            renderState.startRead();

            Input.update();

            Renderer.getInstance().startFrame();
            latestDrawResult.reset();
            Renderer.getInstance().draw(latestDrawResult, renderState.getCurrentReadState());
            latestDrawResult.GPUProfilingResult = GPUProfiler.dumpTimings();
            latestDrawResult.setFinished();
            Renderer.getInstance().endFrame();
            Engine.getEventBus().post(new FrameFinishedEvent(latestDrawResult));
            scene.endFrame();

            renderState.stopRead();
            return null;
        }
    };
    protected void actuallyDraw() {
        try {
            GraphicsContext.getInstance().execute(drawCallable).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
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

            for(int i = 0; i < modelComponent.getMeshes().size(); i++) {
                Mesh mesh = modelComponent.getMeshes().get(i);
                if(mesh.getMaterial() != null) mesh.getMaterial().setTexturesUsed();
                PerMeshInfo info = renderState.getCurrentWriteState().entitiesState.cash.get(mesh);
                if(info == null) {
                    info = new PerMeshInfo();
                    renderState.getCurrentWriteState().entitiesState.cash.put(mesh, info);
                }
                info.init(firstpassDefaultProgram, entityIndexOf+i*entity.getInstanceCount(), entity.isVisible(), entity.isSelected(), Config.getInstance().isDrawLines(), cameraWorldPosition, isInReachForTextureLoading, entity.getInstanceCount(), visibleForCamera, entity.getUpdate(), entity.getMinMaxWorld()[0], entity.getMinMaxWorld()[1], entity.getMinMaxWorldVec3()[0], entity.getMinMaxWorldVec3()[1], info.getCenterWorld(), modelComponent.getIndexCount(i), modelComponent.getIndexOffset(i), modelComponent.getBaseVertex(i), entity.getLastMovedInCycle());
                renderState.getCurrentWriteState().add(info);
            }
        }
    }

    private void initOpenGLContext() {
        GraphicsContext.getInstance();
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
            renderState1.setIndexBuffer(scene.getIndexBuffer());
            renderState1.setVertexBuffer(scene.getVertexBuffer());
        });
        sceneIsInitiallyDrawn = false;
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
        directionalLightMovedInCycle = cycle.get(); // TODO: This is not correct, but forces vct light injeciton and directional shadow map redraw, fix this, mate
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
        directionalLightMovedInCycle = cycle.get(); // TODO: This is not correct, but forces vct light injeciton and directional shadow map redraw, fix this, mate
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

    private void destroyOpenGL() {
        GraphicsContext.getInstance().getDrawThread().stopRequested = true;
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

        GraphicsContext.exitOnGLError("loadTestScene");

        try {
//            Mesh skyBox = new OBJLoader().loadTexturedModel(new File(Engine.WORKDIR_NAME + "/assets/models/skybox.obj")).get(0);
//            Entity skyBoxEntity = EntityFactory.getInstance().getEntity(new Vector3f(), skyBox);
//            skyBoxEntity.setScale(100);
//            entities.add(skyBoxEntity);

            Model sphere = new OBJLoader().loadTexturedModel(new File(Engine.WORKDIR_NAME + "/assets/models/sphere.obj"));

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
                            Entity entity = EntityFactory.getInstance().getEntity(position, "Entity_" + System.currentTimeMillis(), sphere);
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
//			List<Mesh> sponza = renderer.getOBJLoader().loadTexturedModel(new File(Engine.WORKDIR_NAME + "/assets/models/sponza.obj"));
//			for (Mesh model : sponza) {
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
//			List<Mesh> skyBox = renderer.getOBJLoader().loadTexturedModel(new File(Engine.WORKDIR_NAME + "/assets/models/skybox.obj"));
//			for (Mesh model : skyBox) {
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
