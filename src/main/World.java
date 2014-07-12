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
import main.model.Entity;
import main.model.IEntity;
import main.model.Model;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.renderer.light.Spotlight;
import main.renderer.material.Material;
import main.renderer.material.Material.MAP;
import main.scene.Scene;
import main.texture.Texture;
import main.util.gui.DebugFrame;
import main.util.stopwatch.OpenGLStopWatch;
import main.util.stopwatch.StopWatch;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector3f;

import com.alee.laf.WebLookAndFeel;

public class World {
	public static final String WORKDIR_NAME = "hp";
	public static final String ASSETDIR_NAME = "hp/assets";

	private static Logger LOGGER = getLogger();
	public static boolean RELOAD_ON_FILE_CHANGE = (java.lang.management.ManagementFactory.getRuntimeMXBean().
		    getInputArguments().toString().indexOf("-agentlib:jdwp") > 0);
	
	private OpenGLStopWatch glWatch;

	public static Spotlight light= new Spotlight(true);
	public static volatile boolean useParallaxLocation = false;
	public static volatile boolean useParallax = true;
	public static volatile boolean useSteepParallaxLocation = false;
	public static volatile boolean useSteepParallax = false;
	public static volatile boolean useAmbientOcclusion = true;
	public static volatile boolean useFrustumCulling = true;
	public static volatile boolean DRAWLINES_ENABLED = false;
	public static volatile boolean DEBUGFRAME_ENABLED = false;
	public static volatile boolean DRAWLIGHTS_ENABLED = false;

	public static float AMBIENTOCCLUSION_TOTAL_STRENGTH = 1f;
	public static float AMBIENTOCCLUSION_RADIUS = 0.0125f;

	public static Vector3f AMBIENT_LIGHT = new Vector3f(0.5f, 0.5f,0.5f);
	
	public static void main(String[] args) {
		final World world;
		
		String sceneName;
		if (args.length > 0) {
			sceneName = args[0];
		} else {
			sceneName = "default";
		}

		world = new World(sceneName);
		
		WebLookAndFeel.install();
		new DebugFrame(world);
		
		world.simulate();
	}

	Scene scene;
	private int entityCount = 10;
	public Renderer renderer;
	private Camera camera;

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
		scene = new Scene();
		camera = new Camera(renderer);
		try {
			light.init(renderer);
		} catch (Exception e) {
			e.printStackTrace();
		}
		initDefaultMaterials();
		scene.addAll(loadDummies());
		scene.setInitialized(true);
	}
	
	public World(String sceneName) {
		initWorkDir();
		renderer = new DeferredRenderer(light);
		glWatch = new OpenGLStopWatch();
		initDefaultMaterials();
		scene = Scene.read(renderer, sceneName);
		scene.init(renderer);
		camera = new Camera(renderer);
		try {
			light.init(renderer);
		} catch (Exception e) {
			e.printStackTrace();
		}
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
			List<Model> sphere = renderer.getOBJLoader().loadTexturedModel(new File("C:\\sphere.obj"));
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
						Vector3f position = new Vector3f(i*10,0-random*i+j,j*10);
						IEntity entity = renderer.getEntityFactory().getEntity(position, "Entity_" + sphere.get(0).getName() + Entity.count++, sphere.get(0), mat);
						entity.setMaterial(mat);
						Vector3f scale = new Vector3f(0.5f, 0.5f, 0.5f);
						scale.scale(new Random().nextFloat()*14);
						entity.setScale(scale);
						entities.add(entity);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

			StopWatch.getInstance().start("Load Sponza");
//			List<Model> sponza = OBJLoader.loadTexturedModel(new File("C:\\san-miguel-converted\\san-miguel.obj"));
			List<Model> sponza = renderer.getOBJLoader().loadTexturedModel(new File("C:\\crytek-sponza-converted\\sponza.obj"));
			for (Model model : sponza) {
//				model.setMaterial(mirror);
//				if(model.getMaterial().getName().contains("fabric")) {
//					model.setMaterial(mirror);
//				}
				IEntity entity = renderer.getEntityFactory().getEntity(new Vector3f(0,-1f,0), model);
//				Vector3f scale = new Vector3f(3.1f, 3.1f, 3.1f);
//				entity.setScale(scale);
				entities.add(entity);
			}
			List<Model> skyBox = renderer.getOBJLoader().loadTexturedModel(new File("C:\\skybox.obj"));
			for (Model model : skyBox) {
				IEntity entity = renderer.getEntityFactory().getEntity(new Vector3f(0,0,0), model.getName(), model, mirror);
				Vector3f scale = new Vector3f(1000, 1000f, 1000f);
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
		if (Keyboard.isKeyDown(Keyboard.KEY_UP)) {
			light.rotate(new Vector3f(1,0,0), camera.getRotationSpeed()/100);
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) {
			light.rotate(new Vector3f(1,0,0), -camera.getRotationSpeed()/100);
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) {
			light.rotate(new Vector3f(0,1,0), camera.getRotationSpeed()/100);
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) {
			light.rotate(new Vector3f(0,1,0), -camera.getRotationSpeed()/100);
		}
//		System.out.println("LightPosition: " + lightPosition);
//		for (IEntity entity : entities) {
//			float random = (float) (Math.random() -1f );
//			entity.getPosition().x += 0.01 * random;
//		}
		StopWatch.getInstance().stopAndPrintMS();
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
			renderer.drawDebug(camera, scene.getOctree(), scene.getEntities(), light);
		} else {
			renderer.draw(camera, scene.getOctree(), scene.getEntities(), light);
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

	public Scene getScene() {
		return scene;
	}

	public void setScene(Scene scene) {
		this.scene = scene;
	}
}