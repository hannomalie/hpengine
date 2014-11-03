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
import main.component.IGameComponent.ComponentIdentifier;
import main.component.PhysicsComponent;
import main.model.Entity;
import main.model.IEntity;
import main.model.Model;
import main.physic.PhysicsFactory;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.renderer.light.PointLight;
import main.renderer.light.Spotlight;
import main.renderer.material.Material;
import main.renderer.material.Material.MAP;
import main.scene.Scene;
import main.texture.Texture;
import main.util.gui.DebugFrame;
import main.util.stopwatch.OpenGLStopWatch;
import main.util.stopwatch.StopWatch;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector3f;

import com.alee.laf.WebLookAndFeel;
import com.bulletphysics.collision.broadphase.BroadphaseInterface;
import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.collision.dispatch.CollisionConfiguration;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.shapes.CollisionShape;
import com.bulletphysics.collision.shapes.SphereShape;
import com.bulletphysics.collision.shapes.StaticPlaneShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.ConstraintSolver;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.MotionState;

public class World {
	public static final String WORKDIR_NAME = "hp";
	public static final String ASSETDIR_NAME = "hp/assets";

	private static Logger LOGGER = getLogger();
	public static boolean RELOAD_ON_FILE_CHANGE = false;//(java.lang.management.ManagementFactory.getRuntimeMXBean().
//		    getInputArguments().toString().indexOf("-agentlib:jdwp") > 0);
	
	private OpenGLStopWatch glWatch;

	public static Spotlight light= new Spotlight(true);
	public static volatile boolean useParallax = false;
	public static volatile boolean useSteepParallax = false;
	public static volatile boolean useAmbientOcclusion = true;
	public static volatile boolean useColorBleeding = false;
	public static volatile boolean useFrustumCulling = false;
	public static volatile boolean useInstantRadiosity = false;
	public static volatile boolean DRAWLINES_ENABLED = false;
	public static volatile boolean DRAWSCENE_ENABLED = true;
	public static volatile boolean DEBUGFRAME_ENABLED = false;
	public static volatile boolean DRAWLIGHTS_ENABLED = false;
	public static volatile boolean DRAW_PROBES = true;

	public static float AMBIENTOCCLUSION_TOTAL_STRENGTH = 0.5f;
	public static float AMBIENTOCCLUSION_RADIUS = 0.0250f;
	public static int EXPOSURE = 4;

	public static Vector3f AMBIENT_LIGHT = new Vector3f(0.1f, 0.1f,0.1f);
	
	public static void main(String[] args) {
		final World world;
		
		String sceneName = "sponza";
		boolean debug = true;
		for (String string : args) {
			if("debug=false".equals(string)) {
				debug = false;
			} else {
				sceneName = string;
				break;
			}
		}

		world = new World(sceneName);
//		world = new World();
		
		WebLookAndFeel.install();
		if(debug) {
			new DebugFrame(world);
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
		initWorkDir();
		renderer = new DeferredRenderer(light);
		glWatch = new OpenGLStopWatch();
		physicsFactory = new PhysicsFactory(this);
		scene = new Scene(renderer);
		camera = new Camera(renderer);
		activeCamera = camera;
		try {
			light.init(renderer);
		} catch (Exception e) {
			e.printStackTrace();
		}
		initDefaultMaterials();
		scene.addAll(loadDummies());
		scene.setInitialized(true);
		renderer.init(scene.getOctree());
	}
	
	public World(String sceneName) {
		initWorkDir();
		renderer = new DeferredRenderer(light);
		glWatch = new OpenGLStopWatch();
		physicsFactory = new PhysicsFactory(this);
		initDefaultMaterials();
		scene = Scene.read(renderer, sceneName);
		scene.init(renderer);
		camera = new Camera(renderer);
		activeCamera = camera;
		try {
			light.init(renderer);
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
	}
	
	private void initDefaultMaterials() {

		white = renderer.getMaterialFactory().getMaterial("default", new HashMap<MAP,String>(){{
														put(MAP.DIFFUSE,"assets/textures/default.dds");
																}});

		stone = renderer.getMaterialFactory().getMaterial("stone", new HashMap<MAP,String>(){{
														put(MAP.DIFFUSE,"assets/textures/stone_diffuse.png");
														put(MAP.NORMAL,"assets/textures/stone_normal.png");
																}});
		
		stone2 = renderer.getMaterialFactory().getMaterial("stone2", new HashMap<MAP,String>(){{
														    		put(MAP.DIFFUSE,"assets/textures/brick.png");
														    		put(MAP.NORMAL,"assets/textures/brick_normal.png");
																}});
		
		wood = renderer.getMaterialFactory().getMaterial("wood", new HashMap<MAP,String>(){{
														    		put(MAP.DIFFUSE,"assets/textures/wood_diffuse.png");
														    		put(MAP.NORMAL,"assets/textures/wood_normal.png");
																}});
		stoneWet = renderer.getMaterialFactory().getMaterial("stoneWet", new HashMap<MAP,String>(){{
														    		put(MAP.DIFFUSE,"assets/textures/stone_diffuse.png");
														    		put(MAP.NORMAL,"assets/textures/stone_normal.png");
														    		put(MAP.REFLECTION,"assets/textures/stone_reflection.png");
																}});
		mirror = renderer.getMaterialFactory().getMaterial("mirror", new HashMap<MAP,String>(){{
														    		put(MAP.REFLECTION,"assets/textures/default.dds");
																}});
	}

	private List<IEntity> loadDummies() {
		List<IEntity> entities = new ArrayList<>();
		
		Renderer.exitOnGLError("loadDummies");

		try {
			List<Model> sphere = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/sphere.obj"));
			//List<Model> sphere = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/cube.obj"));
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
//				Vector3f scale = new Vector3f(3.1f, 3.1f, 3.1f);
//				entity.setScale(scale);
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
		
		if (Keyboard.isKeyDown(Keyboard.KEY_UP)) {
			light.rotate(new Vector3f(0,0,1), camera.getRotationSpeed()/100);
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) {
			light.rotate(new Vector3f(0,0,1), -camera.getRotationSpeed()/100);
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) {
			light.rotate(new Vector3f(1,0,0), camera.getRotationSpeed()/100);
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) {
			light.rotate(new Vector3f(1,0,0), -camera.getRotationSpeed()/100);
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_NUMPAD8)) {
			light.move(new Vector3f(0,-1f,0));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_NUMPAD2)) {
			light.move(new Vector3f(0,1f,0));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_NUMPAD4)) {
			light.move(new Vector3f(-1f,0,0));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_NUMPAD6)) {
			light.move(new Vector3f(1f,0,0));
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
		light.update(seconds);
		StopWatch.getInstance().stopAndPrintMS();

		StopWatch.getInstance().start("Entities update");
		scene.update(seconds);
//		RecursiveAction task = new RecursiveEntityUpdate(entities, 0, entities.size(), seconds);
//		fjpool.invoke(task);
		StopWatch.getInstance().stopAndPrintMS();

		Renderer.exitOnGLError("update");
	}

	
	private class RecursiveEntityUpdate extends RecursiveAction {
		final int LIMIT = 3;
		int result;
		int start, end;
		List<IEntity> entities;
		float seconds;

		RecursiveEntityUpdate(List<IEntity> entities, int start, int end, float seconds) {
			this.start = start;
			this.end = end;
			this.entities = entities;
			this.seconds = seconds;
		}
		
		@Override
		protected void compute() {
			if ((end - start) < LIMIT) {
				for (int i = start; i < end; i++) {
					entities.get(i).update(seconds);
				}
			} else {
				int mid = (start + end) / 2;
				RecursiveEntityUpdate left = new RecursiveEntityUpdate(entities, start, mid, seconds);
				RecursiveEntityUpdate right = new RecursiveEntityUpdate(entities, mid, end, seconds);
				left.fork();
				right.fork();
				left.join();
				right.join();
			}
		}
		
	}
	
	private void draw() {

		StopWatch.getInstance().start("Draw");
		if (DRAWLINES_ENABLED) {
			renderer.drawDebug(activeCamera, physicsFactory.getDynamicsWorld(), scene.getOctree(), scene.getEntities(), light);
		} else {
			renderer.draw(activeCamera, scene.getOctree(), scene.getEntities(), light);
		}

		StopWatch.getInstance().stopAndPrintMS();

		Renderer.exitOnGLError("draw in render");
		
	}
	
	private void loopCycle(float seconds) {

//		long millisecondsStart = System.currentTimeMillis();
		update(seconds);
//		System.out.println("update: " + (System.currentTimeMillis() - millisecondsStart) + " ms");
//		long timeSpentInMilliseconds = System.currentTimeMillis() - millisecondsStart;
//		LOGGER.log(Level.INFO, String.format("%d ms for update", timeSpentInMilliseconds));
		draw();
		
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
		this.scene = scene;
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
}