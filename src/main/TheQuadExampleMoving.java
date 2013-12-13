package main;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL41;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL44;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import de.matthiasmann.twl.utils.PNGDecoder;
import de.matthiasmann.twl.utils.PNGDecoder.Format;

public class TheQuadExampleMoving {
	public static final int WIDTH = 800;
	public static final int HEIGHT = 600;
	int fps;
	long lastFPS;
	long lastFrame;
	
	public static int modelMatrixLocation;
	public static int lightPositionLocation;
	public static Vector3f lightPosition = new Vector3f(-3,-3,-3);
	public static int useParallaxLocation;
	public static int useParallax = 0;
	// Entry point for the application
	public static void main(String[] args) {
		new TheQuadExampleMoving();
	}
	
	// Setup variables
	private final String WINDOW_TITLE = "The Quad: Moving";
	// Quad variables
	// Shader variables
	private int pId = 0;
	// Texture variables
	private int[] texIds = new int[] {0, 1, 2, 3, 4};
	private int textureSelector = 0;
	// Moving variables
	private Camera camera = new Camera();
	private FloatBuffer matrix44Buffer = null;
	private List<Entity> entities = new ArrayList<>();
	private int entityCount = 5;
//	private Entity entity = null;
//	private Entity entity2 = null;
//	private Entity quad;
	
	public TheQuadExampleMoving() {
		this.setupOpenGL();
		getDelta();
		lastFPS = Util.getTime();
		
		this.setupQuad();
		this.setupShaders();
		this.setupTextures();
		this.setupMatrices();
		
		Mouse.setCursorPosition(WIDTH/2, HEIGHT/2);
//		try {
//			Mouse.setNativeCursor(new Cursor(1, 1, 0, 0, 1, BufferUtils.createIntBuffer(1), null));
//		} catch (LWJGLException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		while (!Display.isCloseRequested()) {
			this.loopCycle();
			Display.sync(60);
			Display.update();
		}
		
		this.destroyOpenGL();
	}

	private void setupMatrices() {
		matrix44Buffer = BufferUtils.createFloatBuffer(16);
	}

	private void setupTextures() {
		texIds[0] = this.loadTextureToGL("/assets/textures/stone_diffuse.png", pId, "diffuseMap", 0);
		texIds[1] = this.loadTextureToGL("/assets/textures/stone_normal.png", pId, "normalMap", 1);
		texIds[2] = this.loadTextureToGL("/assets/textures/stone_specular.png", pId, "specularMap", 2);
		texIds[3] = this.loadTextureToGL("/assets/textures/stone_occlusion.png", pId, "occlusionMap", 3);
		texIds[4] = this.loadTextureToGL("/assets/textures/stone_height.png", pId, "heightMap", 4);
		
		this.exitOnGLError("setupTexture");
	}

	private void setupOpenGL() {
		try {
			PixelFormat pixelFormat = new PixelFormat();
			ContextAttribs contextAtrributes = new ContextAttribs(4, 2)
				.withForwardCompatible(true);
//				.withProfileCore(true);
			
			Display.setDisplayMode(new DisplayMode(WIDTH, HEIGHT));
			Display.setTitle(WINDOW_TITLE);
			Display.create(pixelFormat, contextAtrributes);
			
			GL11.glViewport(0, 0, WIDTH, HEIGHT);
		} catch (LWJGLException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		
		// Setup an XNA like background color
		GL11.glClearColor(0.4f, 0.6f, 0.9f, 0f);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glEnable(GL11.GL_CULL_FACE);
		
		// Map the internal OpenGL coordinate system to the entire screen
		GL11.glViewport(0, 0, WIDTH, HEIGHT);
		
		this.exitOnGLError("setupOpenGL");
	}
	
	private void setupQuad() {
		
//		quad = new Quad();
		
		this.exitOnGLError("setupQuad");
		
		
		try {
			Model box = OBJLoader.loadTexturedModel(new File("C:\\cube.obj"));
			for (int i = 0; i < entityCount; i++) {
				for (int j = 0; j < entityCount; j++) {
					Entity entity = new Entity(box, new Vector3f(i*2,0,j*2));
					entity.setScale(new Vector3f(0.1f, 0.1f, 0.1f));
					entities.add(entity);
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.exitOnGLError("loadingSuzanne");
	}
	
	private void setupShaders() {
		int vsId = this.loadShader("/assets/shaders/vertex.glsl", GL20.GL_VERTEX_SHADER);
		int fsId = this.loadShader("/assets/shaders/fragment.glsl", GL20.GL_FRAGMENT_SHADER);
		
		pId = GL20.glCreateProgram();
		GL20.glAttachShader(pId, vsId);
		GL20.glAttachShader(pId, fsId);

		GL20.glBindAttribLocation(pId, 0, "in_Position");
		GL20.glBindAttribLocation(pId, 1, "in_Color");
		GL20.glBindAttribLocation(pId, 2, "in_TextureCoord");
		GL20.glBindAttribLocation(pId, 3, "in_Normal");
		GL20.glBindAttribLocation(pId, 4, "in_Binormal");
		GL20.glBindAttribLocation(pId, 5, "in_Tangent");

		GL20.glLinkProgram(pId);
		GL20.glValidateProgram(pId);

		camera.setProjectionMatrixLocation(GL20.glGetUniformLocation(pId,"projectionMatrix"));
		camera.setViewMatrixLocation(GL20.glGetUniformLocation(pId, "viewMatrix"));
		TheQuadExampleMoving.modelMatrixLocation = GL20.glGetUniformLocation(pId, "modelMatrix");
		TheQuadExampleMoving.lightPositionLocation = GL20.glGetUniformLocation(pId, "lightPosition");
		TheQuadExampleMoving.useParallaxLocation = GL20.glGetUniformLocation(pId, "useParallax");

		GL20.glUseProgram(pId);
		
		this.exitOnGLError("setupShaders");
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
		GL20.glUniform3f(TheQuadExampleMoving.lightPositionLocation, lightPosition.x, lightPosition.y, lightPosition.z );
		GL20.glUniform1i(TheQuadExampleMoving.useParallaxLocation, useParallax);
		
		camera.updateControls();
		
		camera.transform();
//		quad.transform();
		for (Entity entity: entities) {
			entity.transform();
		}
		GL20.glUseProgram(pId);
		
		camera.flipBuffers(matrix44Buffer);
		
		this.exitOnGLError("update");
		updateFPS();
	}
	
	private void render() {
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

		GL20.glUseProgram(pId);
		this.exitOnGLError("useProgram in render");
		
//		quad.draw();
		for (Entity entity: entities) {
			entity.draw();
		}
		
		
		this.exitOnGLError("draw in render");
	}
	
	private void loopCycle() {
		this.update();
		this.render();
		
		this.exitOnGLError("loopCycle");
	}
	
	private void destroyOpenGL() {
		// Delete the texture
		GL11.glDeleteTextures(texIds[0]);
		GL11.glDeleteTextures(texIds[1]);
		
		// Delete the shaders
		GL20.glUseProgram(0);
		GL20.glDeleteProgram(pId);

		for (Entity entity: entities) {
			entity.destroy();
		}
		
//		quad.destroy();
		this.exitOnGLError("destroyOpenGL");
		
		Display.destroy();
	}
	
	private int loadShader(String filename, int type) {
		String shaderSource;
		int shaderID = 0;
		
		
		shaderSource = Util.loadAsTextFile(filename);
		
		shaderID = GL20.glCreateShader(type);
		GL20.glShaderSource(shaderID, shaderSource);
		GL20.glCompileShader(shaderID);
		
		if (GL20.glGetShader(shaderID, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
			System.err.println("Could not compile shader.");
			System.exit(-1);
		}
		
		this.exitOnGLError("loadShader");
		
		return shaderID;
	}
	
	private int loadTextureToGL(String filename, int program, String name, int index) {

		TextureBuffer texture = Util.loadPNGTexture(filename, program);

		int texId = GL11.glGenTextures();
		System.out.println("Have " + name + " " + texId);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + index);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, texture.getWidth(), texture.getHeight(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texture.getBuffer());
		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
		
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);

		GL20.glUniform1i(GL20.glGetUniformLocation(program, name), index);
		this.exitOnGLError("loadPNGTexture");

		return texId;
	}
	public void updateFPS() {
		if (Util.getTime() - lastFPS > 1000) {
			Display.setTitle("FPS: " + fps);
			fps = 0;
			lastFPS += 1000;
		}
		fps++;
	}

	public int getDelta() {
		long time = Util.getTime();
		int delta = (int) (time - lastFrame);
		lastFrame = time;
		 
		return delta;
	}
	
	
	public static void exitOnGLError(String errorMessage) {
		int errorValue = GL11.glGetError();
		
		if (errorValue != GL11.GL_NO_ERROR) {
			String errorString = GLU.gluErrorString(errorValue);
			System.err.println("ERROR - " + errorMessage + ": " + errorString);
			
			if (Display.isCreated()) Display.destroy();
			System.exit(-1);
		}
	}
}