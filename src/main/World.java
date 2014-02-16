package main;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import main.util.OBJLoader;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector3f;

public class World {
	
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
			Display.update();
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

		Material stone = new Material(renderer);
		Material wood = new WoodMaterial(renderer);
		try {
			Model box = OBJLoader.loadTexturedModel(new File("C:\\cube.obj"));
			for (int i = 0; i < entityCount; i++) {
				for (int j = 0; j < entityCount; j++) {
					Material mat = stone;
					if (i%2 == 0 || j%2 == 0) {
						mat = wood;
					}
					try {
						float random = (float) (Math.random() * ( 1f - (-1f) ));
						Entity entity = new Entity(renderer, box, new Vector3f(i*2,0-random*i+j,j*2), mat);
						Vector3f scale = new Vector3f(0.5f, 0.5f, 0.5f);
						scale.scale(new Random().nextFloat()*2);
						entity.setScale(scale);
						entities.add(entity);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			
			Model sponza = OBJLoader.loadTexturedModel(new File("C:\\sponza.obj"));
			Entity entity = new Entity(renderer, sponza, new Vector3f(), stone);
			Vector3f scale = new Vector3f(3.1f, 3.1f, 3.1f);
			entity.setScale(scale);
			entities.add(entity);
			
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
			light.getDirection().z += 1f;
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) {
			light.getDirection().z -= 1f;
		}
//		System.out.println("LightPosition: " + lightPosition);
		for (IEntity entity : entities) {
			float random = (float) (Math.random() -1f );
			entity.getPosition().x += 0.01 * random;
		}
		
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
		renderer.draw(camera, entities, light);

		ForwardRenderer.exitOnGLError("draw in render");
		
	}
	
	private void loopCycle() {
		update();
		draw();
		
		ForwardRenderer.exitOnGLError("loopCycle");
	}
	
}