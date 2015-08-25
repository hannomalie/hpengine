package engine;

import camera.Camera;
import com.alee.laf.WebLookAndFeel;
import com.google.common.eventbus.EventBus;
import component.InputControllerComponent;
import component.ModelComponent;
import config.Config;
import engine.model.Entity;
import engine.model.EntityFactory;
import engine.model.Model;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import physic.PhysicsFactory;
import renderer.DeferredRenderer;
import renderer.drawstrategy.GBuffer;
import renderer.Renderer;
import renderer.command.Result;
import renderer.command.Command;
import renderer.fps.FPSCounter;
import renderer.light.DirectionalLight;
import renderer.material.Material;
import scene.EnvironmentProbe;
import scene.Scene;
import texture.Texture;
import util.gui.DebugFrame;
import util.script.ScriptManager;
import util.stopwatch.OpenGLStopWatch;
import util.stopwatch.StopWatch;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.SynchronousQueue;
import java.util.logging.Logger;

import static log.ConsoleLogger.getLogger;

public class AppContext {

	private static AppContext instance = null;
	private TimeStepThread thread;

	public static AppContext getInstance() {
		if(instance == null) {
			throw new IllegalStateException("Call AppContext.init() before using it");
		}
		return instance;
	}

	public static final String WORKDIR_NAME = "hp";
	public static final String ASSETDIR_NAME = "hp/assets";
	private static EventBus eventBus;
	ScriptManager scriptManager;
	PhysicsFactory physicsFactory;
	EntityFactory entityFactory;
	Scene scene;
	private int entityCount = 10;
	public volatile int PICKING_CLICK = 0;
	public Renderer renderer;
	private Camera camera;
	private Camera activeCamera;

	private static Logger LOGGER = getLogger();
	private volatile boolean initialized;

	private OpenGLStopWatch glWatch;

	public static void main(String[] args) {
		String sceneName = "sponza";
		boolean debug = true;
		for (String string : args) {
			if("debug=false".equals(string)) {
				debug = false;
			} else if("secondpassscale=0.5".equals(string)){
				GBuffer.SECONDPASSSCALE = 0.5f;
			} else if("fullhd".equals(string)){
				Config.WIDTH = 1920;
				Config.HEIGHT = 1080;
			} else {
				sceneName = string;
				break;
			}
		}

		init();

//		if(sceneName != null) {
//			Scene scene = Scene.read(AppContext.getInstance().getRenderer(), sceneName);
//            AppContext.getInstance().setScene(scene);
//		}

		WebLookAndFeel.install();
		if(debug) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					new DebugFrame(AppContext.getInstance());
				}
			}).start();
		}
	}

	public static void init() {
		init(false);
	}
	public static void init(boolean headless) {
		new AppContext(headless);
	}

	private AppContext(boolean headless) {
        instance = this;
		initWorkDir();
		entityFactory = new EntityFactory(this);
		renderer = new DeferredRenderer(headless);
		renderer.init(this);
		while(!renderer.isInitialized()) {

		}
		glWatch = new OpenGLStopWatch();
		scriptManager = new ScriptManager(this);
		physicsFactory = new PhysicsFactory(this);

		scene = new Scene();
        AppContext self = this;
        SynchronousQueue<Result<Object>> queue = renderer.addCommand(new Command<Result<Object>>() {
            @Override
            public Result<Object> execute(AppContext appContext) {
                scene.init(self);
                return new Result<Object>(true);
            }
        });
        queue.poll();
		float rotationDelta = 25f;
		float scaleDelta = 0.1f;
		float posDelta = 10f;
		camera = (Camera) new Camera().
                addComponent(new InputControllerComponent() {
                                 private static final long serialVersionUID = 1L;

                                 @Override
                                 public void update(float seconds) {

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
		camera.init(this);
		camera.setPosition(new Vector3f(0, 20, 0));
		activeCamera = camera;
		// TODO: Check if this is still necessary
		activeCamera.rotateWorld(new Vector4f(0, 1, 0, 0.01f));
		activeCamera.rotateWorld(new Vector4f(1, 0, 0, 0.01f));
//		try {
//			renderer.getLightFactory().getDirectionalLight().init(renderer);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
		initialized = true;
		simulate();
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

		thread = new TimeStepThread("World Main", 0.001f){
			@Override
			public void update(float seconds) {
				self.update(seconds);
			}
		};
		thread.start();
	}
	
	public void destroy() {
		SynchronousQueue<Result<Object>> queue = renderer.addCommand(new Command<Result<Object>>() {
			@Override
			public Result<Object> execute(AppContext world) {
				world.getRenderer().destroy();
				return new Result<Object>(new Object());
			}
		});
		queue.poll();
		System.exit(0);
	}

	private List<Entity> loadDummies() {
		List<Entity> entities = new ArrayList<>();
		
		Renderer.exitOnGLError("loadDummies");

		try {
			List<Model> sphere = renderer.getOBJLoader().loadTexturedModel(new File(AppContext.WORKDIR_NAME + "/assets/models/sphere.obj"));
//			List<Model> sphere = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/cube.obj"));
			for (int i = 0; i < entityCount; i++) {
				for (int j = 0; j < entityCount; j++) {
					Material mat = renderer.getMaterialFactory().get("mirror");
					if (i%4 == 1) {
						mat = renderer.getMaterialFactory().get("stone");
					}
					if (i%4 == 2) {
						mat = renderer.getMaterialFactory().get("wood");
					}
					if (i%4 == 3) {
						mat = renderer.getMaterialFactory().get("stone2");
					}
					if (i%4 == 4) {
						mat = renderer.getMaterialFactory().get("mirror");
					}
					try {
						float random = (float) (Math.random() -0.5);
						Vector3f position = new Vector3f(i*20,random*i+j,j*20);
						Entity entity = getEntityFactory().getEntity(position, "Entity_" + sphere.get(0).getName() + Entity.count++, sphere.get(0), mat);
						entity.getComponent(ModelComponent.class).setMaterial(mat.getName());
						Vector3f scale = new Vector3f(0.5f, 0.5f, 0.5f);
						scale.scale(new Random().nextFloat()*14);
						entity.setScale(scale);
						
//						PhysicsComponent physicsComponent = physicsFactory.addBallPhysicsComponent(entity);
//						physicsComponent.getRigidBody().applyCentralImpulse(new javax.vecmath.Vector3f(10*new Random().nextFloat(), 10*new Random().nextFloat(), 10*new Random().nextFloat()));
//						physicsComponent.getRigidBody().applyTorqueImpulse(new javax.vecmath.Vector3f(0, 100*new Random().nextFloat(), 0));
						
						entities.add(entity);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

			StopWatch.getInstance().start("Load Sponza");
//			List<Model> sponza = OBJLoader.loadTexturedModel(new File("C:\\san-miguel-converted\\san-miguel.obj"));
			List<Model> sponza = renderer.getOBJLoader().loadTexturedModel(new File(AppContext.WORKDIR_NAME + "/assets/models/sponza.obj"));
			for (Model model : sponza) {
//				model.setMaterial(mirror);
//				if(model.getMaterial().getName().contains("fabric")) {
//					model.setMaterial(mirror);
//				}
				Entity entity = getEntityFactory().getEntity(new Vector3f(0,-21f,0), model);
//				physicsFactory.addMeshPhysicsComponent(entity, 0);
				Vector3f scale = new Vector3f(3.1f, 3.1f, 3.1f);
				entity.setScale(scale);
				entities.add(entity);
			}
			List<Model> skyBox = renderer.getOBJLoader().loadTexturedModel(new File(AppContext.WORKDIR_NAME + "/assets/models/skybox.obj"));
			for (Model model : skyBox) {
				Entity entity = getEntityFactory().getEntity(new Vector3f(0,0,0), model.getName(), model, renderer.getMaterialFactory().get("mirror"));
				Vector3f scale = new Vector3f(3000, 3000f, 3000f);
				entity.setScale(scale);
				entities.add(entity);
			}

			StopWatch.getInstance().stopAndPrintMS();
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			return entities;
		}
	}
	

	private boolean STRG_PRESSED_LAST_FRAME = false;
	private void update(float seconds) {

		StopWatch.getInstance().start("Controls update");

		if (PICKING_CLICK == 0 && Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) && Display.isActive()) {
			if(Mouse.isButtonDown(0) && !STRG_PRESSED_LAST_FRAME) {
				PICKING_CLICK = 1;
				STRG_PRESSED_LAST_FRAME = true;
			} else if(Mouse.isButtonDown(1) && !STRG_PRESSED_LAST_FRAME) {
				getScene().getEntities().parallelStream().forEach(e -> { e.setSelected(false); });
			}
		} else {
			STRG_PRESSED_LAST_FRAME = false;
		}
		
		DirectionalLight directionalLight = scene.getDirectionalLight();

		StopWatch.getInstance().stopAndPrintMS();
		physicsFactory.update(seconds);
		StopWatch.getInstance().start("Camera update");
		camera.update(seconds);

		StopWatch.getInstance().stopAndPrintMS();
		StopWatch.getInstance().start("Light update");
		if(directionalLight.hasMoved()) {
			renderer.addCommand(world -> {
				for(EnvironmentProbe probe: renderer.getEnvironmentProbeFactory().getProbes()) {
					renderer.addRenderProbeCommand(probe, true);
				}
                renderer.getEnvironmentProbeFactory().draw(scene.getOctree(), true);
                return new Result(true);
            });
		}
		directionalLight.update(seconds);
		StopWatch.getInstance().stopAndPrintMS();

		StopWatch.getInstance().start("Entities update");
		scene.update(seconds);
        renderer.getLightFactory().update(seconds);
		StopWatch.getInstance().stopAndPrintMS();

        scene.endFrame(activeCamera);
        renderer.endFrame();

		Renderer.exitOnGLError("update");
	}

	public Renderer getRenderer() {
		return renderer;
	}
	
	public PhysicsFactory getPhysicsFactory() {
		return physicsFactory;
	}

	public Scene getScene() {
		return scene;
	}

	public void setScene(Scene scene) {
		renderer.getLightFactory().clearAll();
		this.scene = scene;
		SynchronousQueue<Result> result = renderer.addCommand(new Command<Result>() {
			@Override
			public Result execute(AppContext world) {
				StopWatch.getInstance().start("Scene init");
				scene.init(world);
				StopWatch.getInstance().stopAndPrintMS();
				return new Result(new Object());
			}
		});
        result.poll();
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
		if(eventBus == null) {
			setEventBus(new EventBus());
		}
		return eventBus;
	}

	private static void setEventBus(EventBus eventBus) {
		AppContext.eventBus = eventBus;
	}

	public void setRenderer(Renderer renderer) {
		this.renderer = renderer;
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

	public EntityFactory getEntityFactory() {
		return entityFactory;
	}

    public FPSCounter getFPSCounter() {
        return thread.getFpsCounter();
    }
}