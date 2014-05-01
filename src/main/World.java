package main;
import static main.log.ConsoleLogger.getLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;

import main.util.DebugFrame;
import main.util.OBJLoader;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class World {

	private static Logger LOGGER = getLogger();

	public static Spotlight light= new Spotlight(true);
	public static volatile int useParallaxLocation = 0;
	public static volatile int useParallax = 0;
	public static volatile int useSteepParallaxLocation = 0;
	public static volatile int useSteepParallax = 0;
	public static volatile int useAmbientOcclusion = 0;
	public static int useFrustumCulling = 0;
	public static volatile boolean DEBUGFRAME_ENABLED = false;
	public static volatile boolean DRAWLIGHTS_ENABLED = false;
	
	public static void main(String[] args) {

		final World world = new World();
		new DebugFrame(world);
		world.simulate();
	}
	
	public List<IEntity> entities = new ArrayList<>();
	private int entityCount = 2;
	public Renderer renderer;
	private Camera camera;
	
	public World() {
		renderer = new DeferredRenderer(light);
		camera = new Camera(renderer);
		light.init(renderer);
		this.loadDummies();
		
//		try {
//			Mouse.setNativeCursor(new Cursor(1, 1, 0, 0, 1, BufferUtils.createIntBuffer(1), null));
//		} catch (LWJGLException e) {
//			e.printStackTrace();
//		}
		
	}
	
	public void simulate() {

		while (!Display.isCloseRequested()) {
			this.loopCycle();
//			Display.sync(60);

//			long millisecondsStart = System.currentTimeMillis();
			Display.update();
//			long timeSpentInMilliseconds = System.currentTimeMillis() - millisecondsStart;
//			LOGGER.log(Level.INFO, String.format("%d ms for display update", timeSpentInMilliseconds));
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

		Material stone = new Material(renderer, "", "stone_diffuse.png", "stone_normal.png",
												"stone_specular.png", "stone_occlusion.png",
												"stone_height.png");
		Material wood = new Material(renderer, "", "wood_diffuse.png", "wood_normal.png",
												"wood_specular.png", "wood_occlusion.png",
												"wood_height.png");
		try {
			List<Model> box = OBJLoader.loadTexturedModel(new File("C:\\cube.obj"));
			for (int i = 0; i < entityCount; i++) {
				for (int j = 0; j < entityCount; j++) {
					Material mat = stone;
					if (i%2 == 0 || j%2 == 0) {
						mat = wood;
					}
					try {
						float random = (float) (Math.random() * ( 1f - (-1f) ));
						IEntity entity = new Entity(renderer, box.get(0), new Vector3f(i*2,0-random*i+j,j*2), mat, true);
						Vector3f scale = new Vector3f(0.5f, 0.5f, 0.5f);
						scale.scale(new Random().nextFloat()*4);
						entity.setScale(scale);
						entities.add(entity);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			
			List<Model> sponza = OBJLoader.loadTexturedModel(new File("C:\\sponza\\sponza.obj"));
			for (Model model : sponza) {
//				model.setMaterial(stone);
				Entity entity = new Entity(renderer, model, new Vector3f(0,-1.5f,0), model.getMaterial(),  true);
				Vector3f scale = new Vector3f(3.1f, 3.1f, 3.1f);
//				entity.setScale(scale);
				entities.add(entity);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	

	ForkJoinPool fjpool = new ForkJoinPool(Runtime.getRuntime().availableProcessors()*2);
	private void update() {

//		long start = System.currentTimeMillis();
		if (Keyboard.isKeyDown(Keyboard.KEY_UP)) {
			light.rotate(camera.getRight(), camera.getRotationSpeed());
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) {
			light.rotate(camera.getRight(), -camera.getRotationSpeed());
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) {
			light.rotate(camera.getUp(), camera.getRotationSpeed());
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) {
			light.rotate(camera.getUp(), -camera.getRotationSpeed());
		}
//		System.out.println("LightPosition: " + lightPosition);
//		for (IEntity entity : entities) {
//			float random = (float) (Math.random() -1f );
//			entity.getPosition().x += 0.01 * random;
//		}
//		System.out.println("Controls update: " + (System.currentTimeMillis() - start) + " ms");
//		start = System.currentTimeMillis();
		renderer.update();
//		System.out.println("Renderer update: " + (System.currentTimeMillis() - start) + " ms");
//		start = System.currentTimeMillis();
		camera.update();
//		System.out.println("camera update: " + (System.currentTimeMillis() - start) + " ms");
//		start = System.currentTimeMillis();
		light.update();
//		System.out.println("light update: " + (System.currentTimeMillis() - start) + " ms");

//		long start = System.currentTimeMillis();
		// for (IEntity entity: entities) {
		// entity.update();
		// }
		RecursiveAction task = new RecursiveEntityUpdate(entities, 0, entities.size());
		fjpool.invoke(task);
//		System.out.println("Parallel processing time: " + (System.currentTimeMillis() - start) + " ms");

		Renderer.exitOnGLError("update");
	}

	
	private class RecursiveEntityUpdate extends RecursiveAction {
		final int LIMIT = 3;
		int result;
		int start, end;
		List<IEntity> entities;

		RecursiveEntityUpdate(List<IEntity> entities, int start, int end) {
			this.start = start;
			this.end = end;
			this.entities = entities;
		}
		
		@Override
		protected void compute() {
			if ((end - start) < LIMIT) {
				for (int i = start; i < end; i++) {
					entities.get(i).update();
				}
			} else {
				int mid = (start + end) / 2;
				RecursiveEntityUpdate left = new RecursiveEntityUpdate(entities, start, mid);
				RecursiveEntityUpdate right = new RecursiveEntityUpdate(entities, mid, end);
				left.fork();
				right.fork();
				left.join();
				right.join();
			}
		}
		
	}
	
	private void draw() {
//		long millisecondsStart = System.currentTimeMillis();
		renderer.draw(camera, entities, light);
//		long timeSpentInMilliseconds = System.currentTimeMillis() - millisecondsStart;
//		LOGGER.log(Level.INFO, String.format("%d ms for rendering", timeSpentInMilliseconds));

		Renderer.exitOnGLError("draw in render");
		
	}
	
	private void loopCycle() {

//		long millisecondsStart = System.currentTimeMillis();
		update();
//		System.out.println("update: " + (System.currentTimeMillis() - millisecondsStart) + " ms");
//		long timeSpentInMilliseconds = System.currentTimeMillis() - millisecondsStart;
//		LOGGER.log(Level.INFO, String.format("%d ms for update", timeSpentInMilliseconds));
		draw();
		
		Renderer.exitOnGLError("loopCycle");
	}
	
}