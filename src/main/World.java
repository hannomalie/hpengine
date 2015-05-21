package main;
import static main.log.ConsoleLogger.getLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.logging.Logger;

import main.camera.Camera;
import main.config.Config;
import main.model.Entity;
import main.model.IEntity;
import main.model.Model;
import main.physic.PhysicsFactory;
import main.renderer.DeferredRenderer;
import main.renderer.GBuffer;
import main.renderer.Renderer;
import main.renderer.Result;
import main.renderer.command.Command;
import main.renderer.light.DirectionalLight;
import main.renderer.material.Material;
import main.renderer.material.Material.MAP;
import main.scene.EnvironmentProbe;
import main.scene.Scene;
import main.texture.Texture;
import main.util.Adjustable;
import main.util.Toggable;
import main.util.gui.DebugFrame;
import main.util.stopwatch.OpenGLStopWatch;
import main.util.stopwatch.StopWatch;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import com.alee.laf.WebLookAndFeel;
import com.google.common.eventbus.EventBus;

public class World {
	public static final String WORKDIR_NAME = "hp";
	public static final String ASSETDIR_NAME = "hp/assets";
	private static EventBus eventBus;

	private static Logger LOGGER = getLogger();
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
	
	@Toggable(group = "Quality settings") public static volatile boolean MULTIPLE_DIFFUSE_SAMPLES = false;
	@Toggable(group = "Quality settings") public static volatile boolean MULTIPLE_DIFFUSE_SAMPLES_PROBES = false;
	@Toggable(group = "Quality settings") public static volatile boolean USE_CONETRACING_FOR_DIFFUSE = false;
	@Toggable(group = "Quality settings") public static volatile boolean USE_CONETRACING_FOR_DIFFUSE_PROBES = false;
	@Toggable(group = "Quality settings") public static volatile boolean PRECOMPUTED_RADIANCE = true;
	@Toggable(group = "Quality settings") public static volatile boolean CALCULATE_ACTUAL_RADIANCE = true;
	
	@Toggable(group = "Debug") public static volatile boolean DRAWLINES_ENABLED = false;
	@Toggable(group = "Debug") public static volatile boolean DRAWSCENE_ENABLED = true;
	@Toggable(group = "Debug") public static volatile boolean DEBUGDRAW_PROBES = false;
	@Toggable(group = "Debug") public static volatile boolean DEBUGDRAW_PROBES_WITH_CONTENT = false;
	@Toggable(group = "Quality settings") public static volatile boolean CONTINUOUS_DRAW_PROBES = false;
	@Toggable(group = "Debug") public static volatile boolean DEBUGFRAME_ENABLED = false;
	@Toggable(group = "Debug") public static volatile boolean DRAWLIGHTS_ENABLED = false;
	@Toggable(group = "Quality settings") public static volatile boolean DRAW_PROBES = true;
	@Toggable(group = "Debug") public static volatile boolean VSYNC_ENABLED = false;

	@Toggable(group = "Effects") public static volatile boolean SCATTERING = false;
	@Adjustable(group = "Effects") public static volatile float RAINEFFECT = 0.0f;
	@Adjustable(group = "Effects") public static volatile float AMBIENTOCCLUSION_TOTAL_STRENGTH = 0.5f;
	@Adjustable(group = "Effects") public static volatile float AMBIENTOCCLUSION_RADIUS = 0.0250f;
	@Adjustable(group = "Effects") public static volatile float EXPOSURE = 8f;
	@Toggable(group = "Quality settings") public static volatile boolean AUTO_EXPOSURE_ENABLED = true;
	@Toggable(group = "Quality settings") public static volatile boolean ENABLE_POSTPROCESSING = true;
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

		world = new World(sceneName);
//		world = new World();

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
	public Renderer renderer;
	private Camera camera;
	private Camera activeCamera;

	private Material white;
	private Material stone;
	private Material stone2;
	private Material wood;
	private Material stoneWet;
	private Material mirror;

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
		glWatch = new OpenGLStopWatch();
		physicsFactory = new PhysicsFactory(this);
		initDefaultMaterials();
		if(sceneName != null) {
			long start = System.currentTimeMillis();
			System.out.println(start);
			scene = Scene.read(renderer, sceneName);
			scene.init(renderer);
			System.out.println("Duration: " + (System.currentTimeMillis() - start));
		} else {
			scene = new Scene(renderer);
//			scene.addAll(loadDummies());
		}
		camera = new Camera(renderer);
		activeCamera = camera;
		activeCamera.rotateWorld(new Vector4f(0, 1, 0, 0.01f));
		activeCamera.rotateWorld(new Vector4f(1, 0, 0, 0.01f));
		try {
			renderer.getLightFactory().getDirectionalLight().init(renderer);
		} catch (Exception e) {
			e.printStackTrace();
		}
		renderer.init(scene.getOctree());
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
			Display.update();
			StopWatch.getInstance().stopAndPrintMS();
		}
		
		destroy();
		
	}
	
	private void destroy() {
		renderer.destroy();
		System.exit(0);
	}
	
	private void initDefaultMaterials() {

		white = renderer.getMaterialFactory().getMaterial("default", new HashMap<MAP,String>(){{
														put(MAP.DIFFUSE,"hp/assets/textures/default.dds");
																}});

		stone = renderer.getMaterialFactory().getMaterial("stone", new HashMap<MAP,String>(){{
														put(MAP.DIFFUSE,"hp/assets/textures/stone_diffuse.png");
														put(MAP.NORMAL,"hp/assets/textures/stone_normal.png");
																}});
		
		stone2 = renderer.getMaterialFactory().getMaterial("stone2", new HashMap<MAP,String>(){{
														    		put(MAP.DIFFUSE,"hp/assets/textures/brick.png");
														    		put(MAP.NORMAL,"hp/assets/textures/brick_normal.png");
																}});
		
		wood = renderer.getMaterialFactory().getMaterial("wood", new HashMap<MAP,String>(){{
														    		put(MAP.DIFFUSE,"hp/assets/textures/wood_diffuse.png");
														    		put(MAP.NORMAL,"hp/assets/textures/wood_normal.png");
																}});
		stoneWet = renderer.getMaterialFactory().getMaterial("stoneWet", new HashMap<MAP,String>(){{
														    		put(MAP.DIFFUSE,"hp/assets/textures/stone_diffuse.png");
														    		put(MAP.NORMAL,"hp/assets/textures/stone_normal.png");
														    		put(MAP.REFLECTION,"hp/assets/textures/stone_reflection.png");
																}});
		mirror = renderer.getMaterialFactory().getMaterial("mirror", new HashMap<MAP,String>(){{
														    		put(MAP.REFLECTION,"hp/assets/textures/default.dds");
																}});
	}

	private List<IEntity> loadDummies() {
		List<IEntity> entities = new ArrayList<>();
		
		Renderer.exitOnGLError("loadDummies");

		try {
			List<Model> sphere = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/sphere.obj"));
//			List<Model> sphere = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/cube.obj"));
			for (int i = 0; i < entityCount; i++) {
				for (int j = 0; j < entityCount; j++) {
					Material mat = mirror;
					if (i%4 == 1) {
						mat = stone;
					}
					if (i%4 == 2) {
						mat = wood;
					}
					if (i%4 == 3) {
						mat = stone2;
					}
					if (i%4 == 4) {
						mat = mirror;
					}
					try {
						float random = (float) (Math.random() -0.5);
						Vector3f position = new Vector3f(i*20,random*i+j,j*20);
						IEntity entity = renderer.getEntityFactory().getEntity(position, "Entity_" + sphere.get(0).getName() + Entity.count++, sphere.get(0), mat);
						entity.setMaterial(mat.getName());
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
				IEntity entity = renderer.getEntityFactory().getEntity(new Vector3f(0,-21f,0), model);
//				physicsFactory.addMeshPhysicsComponent(entity, 0);
				Vector3f scale = new Vector3f(3.1f, 3.1f, 3.1f);
				entity.setScale(scale);
				entities.add(entity);
			}
			List<Model> skyBox = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/skybox.obj"));
			for (Model model : skyBox) {
				IEntity entity = renderer.getEntityFactory().getEntity(new Vector3f(0,0,0), model.getName(), model, mirror);
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
	private void update(float seconds) {

		StopWatch.getInstance().start("Controls update");

//		if (Mouse.isButtonDown(0)) {
//			List<Model> sphere;
//			try {
//				sphere = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/sphere.obj"));
////				IEntity shot = renderer.getEntityFactory().getEntity(camera.getPosition(), sphere.get(0));
//				PointLight pointLight = renderer.getLightFactory().getPointLight(camera.getPosition(), sphere.get(0));
//				PhysicsComponent physicsBall = physicsFactory.addBallPhysicsComponent(pointLight);
//				physicsBall.getRigidBody().applyCentralImpulse(new javax.vecmath.Vector3f(5,0,5));
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
//			
//		}

		DirectionalLight directionalLight = renderer.getLightFactory().getDirectionalLight();
		
		if (Keyboard.isKeyDown(Keyboard.KEY_UP)) {
			directionalLight.rotateWorld(new Vector3f(0,0,1), camera.getRotationSpeed()/100);
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) {
			directionalLight.rotateWorld(new Vector3f(0,0,1), -camera.getRotationSpeed()/100);
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) {
			directionalLight.rotateWorld(new Vector3f(1,0,0), camera.getRotationSpeed()/100);
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) {
			directionalLight.rotateWorld(new Vector3f(1,0,0), -camera.getRotationSpeed()/100);
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_NUMPAD8)) {
			directionalLight.move(new Vector3f(0,-1f,0));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_NUMPAD2)) {
			directionalLight.move(new Vector3f(0,1f,0));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_NUMPAD4)) {
			directionalLight.move(new Vector3f(-1f,0,0));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_NUMPAD6)) {
			directionalLight.move(new Vector3f(1f,0,0));
		}
//		System.out.println("LightPosition: " + lightPosition);
//		for (IEntity entity : entities) {
//			float random = (float) (Math.random() -1f );
//			entity.getPosition().x += 0.01 * random;
//		}
		StopWatch.getInstance().stopAndPrintMS();
		physicsFactory.update(seconds);
		StopWatch.getInstance().start("Renderer update");
		renderer.update(this, seconds);
		StopWatch.getInstance().stopAndPrintMS();
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
	
	private void draw() {

		StopWatch.getInstance().start("Draw");
		if (DRAWLINES_ENABLED) {
			renderer.drawDebug(activeCamera, physicsFactory.getDynamicsWorld(), scene.getOctree(), scene.getEntities());
		} else {
//			fireRenderProbeCommands();
			renderer.draw(activeCamera, this, scene.getEntities());
		}

		if(counter < 20) {
			renderer.getLightFactory().getDirectionalLight().rotate(new Vector4f(0, 1, 0, 0.001f));
			CONTINUOUS_DRAW_PROBES = true;
			counter++;
		} else if(counter == 20) {
			CONTINUOUS_DRAW_PROBES = false;
		}
		StopWatch.getInstance().stopAndPrintMS();

		Renderer.exitOnGLError("draw in render");
		
	}

	// I need this to force probe redrawing after engine startup....TODO: Find better solution
	int counter = 0;
	
	private void fireRenderProbeCommands() {
//		if(!CONTINUOUS_DRAW_PROBES) { return; }
		for(EnvironmentProbe probe: renderer.getEnvironmentProbeFactory().getProbes()) {
			renderer.addRenderProbeCommand(probe, true);
		}
	}
	
	private void loopCycle(float seconds) {

//		long millisecondsStart = System.currentTimeMillis();
		update(seconds);
//		LOGGER.log(Level.INFO, "update: " + (System.currentTimeMillis() - millisecondsStart) + " ms");
//		System.out.println("update: " + (System.currentTimeMillis() - millisecondsStart) + " ms");
//		long timeSpentInMilliseconds = System.currentTimeMillis() - millisecondsStart;
//		LOGGER.log(Level.INFO, String.format("%d ms for update", timeSpentInMilliseconds));
		draw();
//		LOGGER.log(Level.INFO, "draw: " + (System.currentTimeMillis() - millisecondsStart) + " ms");
		scene.endFrame(activeCamera);
//		LOGGER.log(Level.INFO, "cycle: " + (System.currentTimeMillis() - millisecondsStart) + " ms");

//		Renderer.exitOnGLError("loopCycle");
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
		renderer.addCommand(new Command<Result>() {
			@Override
			public Result execute(World world) {
				scene.init(renderer);
				renderer.init(scene.getOctree());
				return new Result();
			}
		});
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

	public void setActiveCamera(Camera activeCamera) {
		this.activeCamera = activeCamera;
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
}