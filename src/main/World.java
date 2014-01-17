package main;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import javax.swing.DebugGraphics;

import main.util.OBJLoader;
import main.util.Quad;
import main.util.Util;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL41;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL44;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.newdawn.slick.tests.TestUtils;

import de.matthiasmann.twl.utils.PNGDecoder;
import de.matthiasmann.twl.utils.PNGDecoder.Format;

public class World {

//	public static int framebufferLocation;
//	public static int depthbufferLocation;
//	public static int colorTexture;
	
	public static int lightPositionLocation;
	public static Vector3f lightPosition = new Vector3f(-3,-3,-3);
	public static int useParallaxLocation;
	public static int useParallax = 0;
	
	public static void main(String[] args) {
		new World();
	}
	
	private List<IEntity> entities = new ArrayList<>();
	private int entityCount = 5;
	private ForwardRenderer renderer;
	
	public World() {
		renderer = new ForwardRenderer();
		this.setupQuad();
		
//		try {
//			Mouse.setNativeCursor(new Cursor(1, 1, 0, 0, 1, BufferUtils.createIntBuffer(1), null));
//		} catch (LWJGLException e) {
//			e.printStackTrace();
//		}
		
		while (!Display.isCloseRequested()) {
			this.loopCycle();
			Display.sync(60);
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

	private void setupQuad() {
		
//		quad = new Quad();
		
		ForwardRenderer.exitOnGLError("setupQuad");

		Material stone = new Material();
		Material wood = new WoodMaterial();
		try {
			Model box = OBJLoader.loadTexturedModel(new File("C:\\cube.obj"));
			for (int i = 0; i < entityCount; i++) {
				for (int j = 0; j < entityCount; j++) {
					Material mat = stone;
					if (i%2 == 0 || j%2 == 0) {
						mat = wood;
					}
					Entity entity = new Entity(box, new Vector3f(i*2,0,j*2), mat);
					Vector3f scale = new Vector3f(0.1f, 0.1f, 0.1f);
					scale.scale(new Random().nextFloat());
					entity.setScale(scale);
					entities.add(entity);
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	private void update() {

		if (Keyboard.isKeyDown(Keyboard.KEY_UP)) {
			lightPosition.x -= 0.25;
			if (lightPosition.x <= -10) {
				lightPosition.x = 10;
			}
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) {
			lightPosition.x += 0.25;
			if (lightPosition.x >= 10) {
				lightPosition.x = -10;
			}
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) {
			lightPosition.z -= 0.25;
			if (lightPosition.z <= -10) {
				lightPosition.z = 10;
			}
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) {
			lightPosition.z += 0.25;
			if (lightPosition.z >= 10) {
				lightPosition.z = -10;
			}
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_ADD)) {
			lightPosition.y += 0.25;
			if (lightPosition.y >= 10) {
				lightPosition.y = -10;
			}
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_MINUS)) {
			lightPosition.y -= 0.25;
			if (lightPosition.y <= -10) {
				lightPosition.y = 10;
			}
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_1)) {
			if(useParallax == 0) {
				useParallax = 1;
			} else useParallax = 0;
		}
//		System.out.println("LightPosition: " + lightPosition);
		
		renderer.update();
		for (IEntity entity: entities) {
			entity.update();
		}

		GL20.glUniform3f(World.lightPositionLocation, lightPosition.x, lightPosition.y, lightPosition.z );
		GL20.glUniform1i(World.useParallaxLocation, useParallax);
		ForwardRenderer.exitOnGLError("update");
	}
	
	private void draw() {
		renderer.draw(entities);

		ForwardRenderer.exitOnGLError("draw in render");
		
	}
	
	private void loopCycle() {
		update();
		draw();
		
		ForwardRenderer.exitOnGLError("loopCycle");
	}
	
}