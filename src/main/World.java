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

import main.Material.MAP;
import main.octree.Octree;
import main.util.OBJLoader;
import main.util.gui.DebugFrame;
import main.util.stopwatch.OpenGLStopWatch;
import main.util.stopwatch.StopWatch;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector3f;

import com.alee.laf.WebLookAndFeel;
import com.sun.prism.paint.Stop;

public class World {
	public static final String WORKDIR_NAME = "hp";

	private static Logger LOGGER = getLogger();
	public static boolean RELOAD_ON_FILE_CHANGE = (java.lang.management.ManagementFactory.getRuntimeMXBean().
		    getInputArguments().toString().indexOf("-agentlib:jdwp") > 0);
	
	private OpenGLStopWatch glWatch;

	public static Spotlight light= new Spotlight(true);
	public static volatile boolean useParallaxLocation = false;
	public static volatile boolean useParallax = false;
	public static volatile boolean useSteepParallaxLocation = false;
	public static volatile boolean useSteepParallax = false;
	public static volatile boolean useAmbientOcclusion = true;
	public static volatile boolean useFrustumCulling = true;
	public static volatile boolean DRAWLINES_ENABLED = false;
	public static volatile boolean DEBUGFRAME_ENABLED = false;
	public static volatile boolean DRAWLIGHTS_ENABLED = false;

//	public static float AMBIENTOCCLUSION_STRENGTH = 0.07f;
	public static float AMBIENTOCCLUSION_TOTAL_STRENGTH = 1.1f;
	public static float AMBIENTOCCLUSION_RADIUS = 0.012f;
//	public static float AMBIENTOCCLUSION_FALLOFF = 0.0000012f;

	public static Vector3f AMBIENT_LIGHT = new Vector3f(0.5f, 0.5f,0.5f);
	
	public static void main(String[] args) {

		final World world = new World();
		new DebugFrame(world);
		world.simulate();
	}

	public Octree octree;
	public List<IEntity> entities = new ArrayList<>();
	private int entityCount = 10;
	public Renderer renderer;
	private Camera camera;
	
	public World() {
		WebLookAndFeel.install();
		initWorkDir();
		renderer = new DeferredRenderer(light);
		glWatch = new OpenGLStopWatch();
		octree = new Octree(new Vector3f(), 400, 6);
		camera = new Camera(renderer);
		light.init(renderer);
		loadDummies();
		octree.insert(entities);
	}

	private void initWorkDir() {
		File theDir = new File(WORKDIR_NAME);

		// if the directory does not exist, create it
		if (!theDir.exists()) {
			boolean result = theDir.mkdir();
		}
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
		for (IEntity entity: entities) {
			entity.destroy();
		}

		renderer.destroy();
	}

	private void loadDummies() {
		
		Renderer.exitOnGLError("loadDummies");

		Material white = new Material(renderer, "", new HashMap<MAP,String>(){{
														put(MAP.DIFFUSE,"default.dds");
													}});

		Material stone = new Material(renderer, "", new HashMap<MAP,String>(){{
														put(MAP.DIFFUSE,"stone_diffuse.png");
														put(MAP.NORMAL,"stone_normal.png");
													}});
		
		Material stone2 = new Material(renderer, "", new HashMap<MAP,String>(){{
														    		put(MAP.DIFFUSE,"brick.png");
														    		put(MAP.NORMAL,"brick_normal.png");
													}});
		
		Material wood = new Material(renderer, "", new HashMap<MAP,String>(){{
														    		put(MAP.DIFFUSE,"wood_diffuse.png");
														    		put(MAP.NORMAL,"wood_normal.png");
													}});
		Material stoneWet = new Material(renderer, "", new HashMap<MAP,String>(){{
														    		put(MAP.DIFFUSE,"stone_diffuse.png");
														    		put(MAP.NORMAL,"stone_normal.png");
														    		put(MAP.REFLECTION,"stone_reflection.png");
													}});
		Material mirror = new Material(renderer, "", new HashMap<MAP,String>(){{
														    		put(MAP.REFLECTION,"default.dds");
													}});

		StopWatch.getInstance().start("Load Dummies");
		try {
			List<Model> box = OBJLoader.loadTexturedModel(new File("C:\\sphere.obj"));
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
						IEntity entity = new Entity(renderer, box.get(0), new Vector3f(i*10,0-random*i+j,j*10), mat, true);
						Vector3f scale = new Vector3f(0.5f, 0.5f, 0.5f);
						scale.scale(new Random().nextFloat()*14);
						entity.setScale(scale);
						entities.add(entity);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			
//			List<Model> sponza = OBJLoader.loadTexturedModel(new File("C:\\san-miguel-converted\\san-miguel.obj"));
			List<Model> sponza = OBJLoader.loadTexturedModel(new File("C:\\crytek-sponza-converted\\sponza.obj"));
			for (Model model : sponza) {
//				model.setMaterial(mirror);
//				if(model.getMaterial().getName().contains("fabric")) {
//					model.setMaterial(mirror);
//				}
				Entity entity = new Entity(renderer, model, new Vector3f(0,-1f,0), model.getMaterial(),  true);
//				Vector3f scale = new Vector3f(3.1f, 3.1f, 3.1f);
//				entity.setScale(scale);
				entities.add(entity);
			}
			List<Model> skyBox = OBJLoader.loadTexturedModel(new File("C:\\skybox.obj"));
			for (Model model : skyBox) {
				Entity entity = new Entity(renderer, model, new Vector3f(0,0,0), mirror,  true);
				Vector3f scale = new Vector3f(1000, 1000f, 1000f);
				entity.setScale(scale);
				entities.add(entity);
			}

			StopWatch.getInstance().stopAndPrintMS();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	

	ForkJoinPool fjpool = new ForkJoinPool(Runtime.getRuntime().availableProcessors()*2);
	private void update(float seconds) {

		StopWatch.getInstance().start("Controls update");
		if (Keyboard.isKeyDown(Keyboard.KEY_UP)) {
			light.rotate(new Vector3f(1,0,0), camera.getRotationSpeed());
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) {
			light.rotate(new Vector3f(1,0,0), -camera.getRotationSpeed());
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) {
			light.rotate(new Vector3f(0,1,0), camera.getRotationSpeed());
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) {
			light.rotate(new Vector3f(0,1,0), -camera.getRotationSpeed());
		}
//		System.out.println("LightPosition: " + lightPosition);
//		for (IEntity entity : entities) {
//			float random = (float) (Math.random() -1f );
//			entity.getPosition().x += 0.01 * random;
//		}
		StopWatch.getInstance().stopAndPrintMS();
		StopWatch.getInstance().start("Renderer update");
		renderer.update(seconds);
		StopWatch.getInstance().stopAndPrintMS();
		StopWatch.getInstance().start("Camera update");
		camera.update(seconds);
		StopWatch.getInstance().stopAndPrintMS();
		StopWatch.getInstance().start("Light update");
		light.update(seconds);
		StopWatch.getInstance().stopAndPrintMS();

		StopWatch.getInstance().start("Entities update");
		 for (IEntity entity: entities) {
		 entity.update(seconds);
		 }
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
			renderer.drawDebug(camera, octree, entities, light);
		} else {
			renderer.draw(camera, octree, entities, light);
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
	
}