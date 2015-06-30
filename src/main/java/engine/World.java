package engine;

import camera.Camera;
import com.alee.laf.WebLookAndFeel;
import com.google.common.eventbus.EventBus;
import component.CameraComponent;
import component.InputControllerComponent;
import component.ModelComponent;
import config.Config;
import engine.model.Entity;
import engine.model.Model;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import physic.PhysicsFactory;
import renderer.DeferredRenderer;
import renderer.GBuffer;
import renderer.Renderer;
import renderer.Result;
import renderer.command.Command;
import renderer.light.DirectionalLight;
import renderer.material.Material;
import renderer.material.Material.MAP;
import scene.EnvironmentProbe;
import scene.Scene;
import texture.Texture;
import util.Adjustable;
import util.Toggable;
import util.gui.DebugFrame;
import util.stopwatch.OpenGLStopWatch;
import util.stopwatch.StopWatch;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.SynchronousQueue;
import java.util.logging.Logger;

import static log.ConsoleLogger.getLogger;

public class World {
	public static final String WORKDIR_NAME = "hp";
	public static final String ASSETDIR_NAME = "hp/assets";
	private static EventBus eventBus;

	private static Logger LOGGER = getLogger();
	private volatile boolean initialized;
//	public static boolean RELOAD_ON_FILE_CHANGE = false;//(java.lang.management.ManagementFactory.getRuntimeMXBean().
//		    getInputArguments().toString().indexOf("-agentlib:jdwp") > 0);
	
	private OpenGLStopWatch glWatch;

//	public static DirectionalLight light= new DirectionalLight(true);
	
	@Toggable(group = "Quality settings") public static volatile boolean useParallax = false;
	@Toggable(group = "Quality settings") public static volatile boolean useSteepParallax = false;
	@Toggable(group = "Quality settings") public static volatile boolean useAmbientOcclusion = true;
	@Toggable(group = "Debug") public static volatile boolean useFrustumCulling = true;
	public static volatile boolean useInstantRadiosity = false;
	@Toggable(group = "Quality settings") public static volatile boolean USE_GI = true;
	@Toggable(group = "Quality settings") public static volatile boolean useSSR = false;
	
	@Toggable(group = "Quality settings") public static volatile boolean MULTIPLE_DIFFUSE_SAMPLES = true;
	@Toggable(group = "Quality settings") public static volatile boolean MULTIPLE_DIFFUSE_SAMPLES_PROBES = true;
	@Toggable(group = "Quality settings") public static volatile boolean USE_CONETRACING_FOR_DIFFUSE = false;
	@Toggable(group = "Quality settings") public static volatile boolean USE_CONETRACING_FOR_DIFFUSE_PROBES = false;
	@Toggable(group = "Quality settings") public static volatile boolean USE_CONETRACING_FOR_SPECULAR = false;
	@Toggable(group = "Quality settings") public static volatile boolean USE_CONETRACING_FOR_SPECULAR_PROBES = false;
	@Toggable(group = "Quality settings") public static volatile boolean PRECOMPUTED_RADIANCE = true;
	@Toggable(group = "Quality settings") public static volatile boolean CALCULATE_ACTUAL_RADIANCE = true;
	@Toggable(group = "Quality settings") public static volatile boolean SSR_FADE_TO_SCREEN_BORDERS = true;
	@Toggable(group = "Quality settings") public static volatile boolean SSR_TEMPORAL_FILTERING = true;
	@Toggable(group = "Quality settings") public static volatile boolean USE_PCF = false;
	
	@Toggable(group = "Debug") public static volatile boolean DRAWLINES_ENABLED = false;
	@Toggable(group = "Debug") public static volatile boolean DRAWSCENE_ENABLED = true;
	@Toggable(group = "Debug") public static volatile boolean DEBUGDRAW_PROBES = false;
	@Toggable(group = "Debug") public static volatile boolean DEBUGDRAW_PROBES_WITH_CONTENT = false;
	@Toggable(group = "Quality settings") public static volatile boolean CONTINUOUS_DRAW_PROBES = false;
	@Toggable(group = "Debug") public static volatile boolean DEBUGFRAME_ENABLED = false;
	@Toggable(group = "Debug") public static volatile boolean DRAWLIGHTS_ENABLED = false;
	@Toggable(group = "Quality settings") public static volatile boolean DRAW_PROBES = true;
	@Toggable(group = "Debug") public static volatile boolean VSYNC_ENABLED = false;

	@Adjustable(group = "Debug") public static volatile float CAMERA_SPEED = 1.0f;
	
	@Toggable(group = "Effects") public static volatile boolean SCATTERING = true;
	@Adjustable(group = "Effects") public static volatile float RAINEFFECT = 0.0f;
	@Adjustable(group = "Effects") public static volatile float AMBIENTOCCLUSION_TOTAL_STRENGTH = 0.5f;
	@Adjustable(group = "Effects") public static volatile float AMBIENTOCCLUSION_RADIUS = 0.0250f;
	@Adjustable(group = "Effects") public static volatile float EXPOSURE = 8f;
	@Toggable(group = "Effects") public static volatile boolean USE_BLOOM = true;
	@Toggable(group = "Effects") public static volatile boolean AUTO_EXPOSURE_ENABLED = true;
	@Toggable(group = "Effects") public static volatile boolean ENABLE_POSTPROCESSING = true;
	public static Vector3f AMBIENT_LIGHT = new Vector3f(1f, 1f, 1f);
	
	public static void main(String[] args) {
		final World world;
		
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

//		world = new World(sceneName);
		world = new World();

		WebLookAndFeel.install();
		if(debug) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					new DebugFrame(world);
				}
			}).start();
//			new DebugFrame(world);
		}

		world.simulate();
	}

	PhysicsFactory physicsFactory;
	Scene scene;
	private int entityCount = 10;
	public volatile int PICKING_CLICK = 0;
	public Renderer renderer;
	private Entity camera;
	private Entity activeCamera;

	public World() {
		this(null);
	}
	public World(boolean headless) {
		this(null, headless);
	}
	public World(String sceneName) {
		this(sceneName, false);
	}
	public World(String sceneName, boolean headless) {
		initWorkDir();
		renderer = new DeferredRenderer(this, headless);
		while(!renderer.isInitialized()) {

		}
		glWatch = new OpenGLStopWatch();
		physicsFactory = new PhysicsFactory(this);
		if(sceneName != null) {
			long start = System.currentTimeMillis();
			System.out.println(start);
			scene = Scene.read(renderer, sceneName);
			scene.init(this);
			System.out.println("Duration: " + (System.currentTimeMillis() - start));
		} else {
			scene = new Scene();
			scene.init(this);
//			scene.addAll(loadDummies());
		}

		float rotationDelta = 45f;
		float scaleDelta = 0.1f;
		float posDelta = 180f;
		camera = renderer.getEntityFactory().getEntity().
					addComponent(new CameraComponent(new Camera(renderer))).
					addComponent(new InputControllerComponent() {
						 @Override public void update(float seconds) {

							float turbo = 1f;
							if(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
								turbo = 3f;
							}

							float rotationAmount = 1.1f * turbo*rotationDelta * seconds * World.CAMERA_SPEED;
							if (Mouse.isButtonDown(0)) {
								getEntity().rotate(Transform.WORLD_UP, Mouse.getDX() * rotationAmount);
							}
							if (Mouse.isButtonDown(1)) {
								getEntity().rotate(Transform.WORLD_RIGHT, -Mouse.getDY() * rotationAmount);
							}
							if (Mouse.isButtonDown(2)) {
								getEntity().rotate(Transform.WORLD_VIEW, Mouse.getDX() * rotationAmount);
							}

							float moveAmount = turbo*posDelta * seconds * World.CAMERA_SPEED;
							if (Keyboard.isKeyDown(Keyboard.KEY_W)) {
								getEntity().move(new Vector3f(0, 0, moveAmount));
							}
							if (Keyboard.isKeyDown(Keyboard.KEY_A)) {
								getEntity().move(new Vector3f(moveAmount, 0, 0));
							}
							if (Keyboard.isKeyDown(Keyboard.KEY_S)) {
								getEntity().move(new Vector3f(0, 0, -moveAmount));
							}
							if (Keyboard.isKeyDown(Keyboard.KEY_D)) {
								getEntity().move(new Vector3f(-moveAmount, 0, 0));
							}
							if (Keyboard.isKeyDown(Keyboard.KEY_Q)) {
								getEntity().move(new Vector3f(0, moveAmount, 0));
							}
							if (Keyboard.isKeyDown(Keyboard.KEY_E)) {
								getEntity().move(new Vector3f(0, -moveAmount, 0));
							}
						 }
					 }
					);
		activeCamera = camera;
		activeCamera.rotateWorld(new Vector4f(0, 1, 0, 0.01f));
		activeCamera.rotateWorld(new Vector4f(1, 0, 0, 0.01f));
//		try {
//			renderer.getLightFactory().getDirectionalLight().init(renderer);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
		renderer.init(scene.getOctree());

		initialized = true;
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

		while (!Display.isCloseRequested()) {
			this.loopCycle(renderer.getElapsedSeconds());
//			Display.sync(60);

			StopWatch.getInstance().start("Display update");
//			Display.update();
			StopWatch.getInstance().stopAndPrintMS();
		}
		
		destroy();
		
	}
	
	private void destroy() {
		renderer.destroy();
		System.exit(0);
	}

	private List<Entity> loadDummies() {
		List<Entity> entities = new ArrayList<>();
		
		Renderer.exitOnGLError("loadDummies");

		try {
			List<Model> sphere = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/sphere.obj"));
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
						Entity entity = renderer.getEntityFactory().getEntity(position, "Entity_" + sphere.get(0).getName() + Entity.count++, sphere.get(0), mat);
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
			List<Model> sponza = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/sponza.obj"));
			for (Model model : sponza) {
//				model.setMaterial(mirror);
//				if(model.getMaterial().getName().contains("fabric")) {
//					model.setMaterial(mirror);
//				}
				Entity entity = renderer.getEntityFactory().getEntity(new Vector3f(0,-21f,0), model);
//				physicsFactory.addMeshPhysicsComponent(entity, 0);
				Vector3f scale = new Vector3f(3.1f, 3.1f, 3.1f);
				entity.setScale(scale);
				entities.add(entity);
			}
			List<Model> skyBox = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/skybox.obj"));
			for (Model model : skyBox) {
				Entity entity = renderer.getEntityFactory().getEntity(new Vector3f(0,0,0), model.getName(), model, renderer.getMaterialFactory().get("mirror"));
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
	

	ForkJoinPool fjpool = new ForkJoinPool(Runtime.getRuntime().availableProcessors()*2);
	private boolean STRG_PRESSED_LAST_FRAME = false;
	private void update(float seconds) {

		StopWatch.getInstance().start("Controls update");

//		if (Mouse.isButtonDown(0)) {
//			List<Model> sphere;
//			try {
//				sphere = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/sphere.obj"));
////				Entity shot = renderer.getEntityFactory().getEntity(camera.getPosition(), sphere.get(0));
//				PointLight pointLight = renderer.getLightFactory().getPointLight(camera.getPosition(), sphere.get(0));
//				PhysicsComponent physicsBall = physicsFactory.addBallPhysicsComponent(pointLight);
//				physicsBall.getRigidBody().applyCentralImpulse(new javax.vecmath.Vector3f(5,0,5));
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
//			
//		}

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
		
		DirectionalLight directionalLight = renderer.getLightFactory().getDirectionalLight();

//		System.out.println("LightPosition: " + lightPosition);
//		for (Entity entity : entities) {
//			float random = (float) (Math.random() -1f );
//			entity.getPosition().x += 0.01 * random;
//		}
		StopWatch.getInstance().stopAndPrintMS();
		physicsFactory.update(seconds);
//		StopWatch.getInstance().start("Renderer update");
//		renderer.update(this, seconds);
//		StopWatch.getInstance().stopAndPrintMS();
		StopWatch.getInstance().start("Camera update");
		camera.update(seconds);

		
		StopWatch.getInstance().stopAndPrintMS();
		StopWatch.getInstance().start("Light update");
		directionalLight.update(seconds);
		StopWatch.getInstance().stopAndPrintMS();

		StopWatch.getInstance().start("Entities update");
		scene.update(seconds);
//		RecursiveAction task = new RecursiveEntityUpdate(entities, 0, entities.size(), seconds);
//		fjpool.invoke(task);
		StopWatch.getInstance().stopAndPrintMS();

		Renderer.exitOnGLError("update");
	}

	private void fireRenderProbeCommands() {
//		if(!CONTINUOUS_DRAW_PROBES) { return; }
		for(EnvironmentProbe probe: renderer.getEnvironmentProbeFactory().getProbes()) {
			renderer.addRenderProbeCommand(probe, true);
		}
	}
	
	private void loopCycle(float seconds) {
		update(seconds);
//		draw();
		scene.endFrame(activeCamera.getComponent(CameraComponent.class).getCamera());
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
			public Result execute(World world) {
				scene.init(world);
				renderer.init(scene.getOctree());
				return new Result();
			}
		});
	}

	public Camera getCamera() {
		return camera.getComponent(CameraComponent.class).getCamera();
	}

	public void setCamera(Camera camera) {
		this.camera.getComponent(CameraComponent.class).setCamera(camera);
	}

	public Camera getActiveCamera() {
		return activeCamera.getComponent(CameraComponent.class).getCamera();
	}

	public Entity getActiveCameraEntity() {
		return activeCamera;
	}

	public void setActiveCamera(Camera activeCamera) {
		this.activeCamera.getComponent(CameraComponent.class).setCamera(activeCamera);
	}

	public static EventBus getEventBus() {
		if(eventBus == null) {
			setEventBus(new EventBus());
		}
		return eventBus;
	}

	private static void setEventBus(EventBus eventBus) {
		World.eventBus = eventBus;
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
}