package main;
import static main.log.ConsoleLogger.getLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import main.util.OBJLoader;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector3f;

public class World {
	private static Logger LOGGER = getLogger();

	public static DirectionalLight light= new DirectionalLight(true);
	public static int useParallaxLocation = 0;
	public static int useParallax = 0;
	
	public static void main(String[] args) {
		new World();
	}
	
	private List<IEntity> entities = new ArrayList<>();
	private int entityCount = 21;
	private ForwardRenderer renderer;
	private Camera camera;
	
	public World() {
		renderer = new ForwardRenderer(light);
		camera = new Camera(renderer);
		light.init(renderer);
		this.loadDummies();
		
//		try {
//			Mouse.setNativeCursor(new Cursor(1, 1, 0, 0, 1, BufferUtils.createIntBuffer(1), null));
//		} catch (LWJGLException e) {
//			e.printStackTrace();
//		}
		
		while (!Display.isCloseRequested()) {
			this.loopCycle();
//			Display.sync(60);

			long millisecondsStart = System.currentTimeMillis();
			Display.update();
			long timeSpentInMilliseconds = System.currentTimeMillis() - millisecondsStart;
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
		
		ForwardRenderer.exitOnGLError("loadDummies");

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
						Entity entity = new Entity(renderer, box.get(0), new Vector3f(i*2,0-random*i+j,j*2), mat, true);
						Vector3f scale = new Vector3f(0.5f, 0.5f, 0.5f);
						scale.scale(new Random().nextFloat()*2);
						entity.setScale(scale);
						entities.add(entity);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			
			List<Model> sponza = OBJLoader.loadTexturedModel(new File("C:\\sponza.obj"));
			for (Model model : sponza) {
				Entity entity = new Entity(renderer, model, new Vector3f(0,-1.5f,0), stone, true);
				Vector3f scale = new Vector3f(3.1f, 3.1f, 3.1f);
				entity.setScale(scale);
				entities.add(entity);	
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	private void update() {

		if (Keyboard.isKeyDown(Keyboard.KEY_UP)) {
			light.getDirection().x += 1f;
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) {
			light.getDirection().x -= 1f;
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) {
			light.getDirection().y += 1f;
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) {
			light.getDirection().y -= 1f;
		}
//		System.out.println("LightPosition: " + lightPosition);
//		for (IEntity entity : entities) {
//			float random = (float) (Math.random() -1f );
//			entity.getPosition().x += 0.01 * random;
//		}
		
		renderer.update();
		camera.update();
		GL20.glUniform3f(renderer.getEyePositionLocation(), camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
		light.update();
		for (IEntity entity: entities) {
			entity.update();
		}

		GL20.glUniform3f(light.lightDirectionLocation, light.getDirection().x, light.getDirection().y, light.getDirection().z );
		GL20.glUniform1i(World.useParallaxLocation, useParallax);
		ForwardRenderer.exitOnGLError("update");
	}
	
	private void draw() {
		long millisecondsStart = System.currentTimeMillis();
		renderer.draw(camera, entities, light);
		long timeSpentInMilliseconds = System.currentTimeMillis() - millisecondsStart;
//		LOGGER.log(Level.INFO, String.format("%d ms for rendering", timeSpentInMilliseconds));

		ForwardRenderer.exitOnGLError("draw in render");
		
	}
	
	private void loopCycle() {

		long millisecondsStart = System.currentTimeMillis();
		update();
		long timeSpentInMilliseconds = System.currentTimeMillis() - millisecondsStart;
//		LOGGER.log(Level.INFO, String.format("%d ms for update", timeSpentInMilliseconds));
		draw();
		
		ForwardRenderer.exitOnGLError("loopCycle");
	}
	
}