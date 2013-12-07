package main;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import main.util.OBJLoader;
import main.util.Quad;
import main.util.Util;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import de.matthiasmann.twl.utils.PNGDecoder;
import de.matthiasmann.twl.utils.PNGDecoder.Format;

public class TheQuadExampleMoving {
	public static final int WIDTH = 800;
	public static final int HEIGHT = 600;
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
	private int[] texIds = new int[] {0, 0};
	private int textureSelector = 0;
	// Moving variables
	private Camera camera = new Camera();
	private FloatBuffer matrix44Buffer = null;
	private Entity entity = new Entity();
	private Entity quad;
	
	public TheQuadExampleMoving() {
		// Initialize OpenGL (Display)
		this.setupOpenGL();
		
		this.setupQuad();
		this.setupShaders();
		this.setupTextures();
		this.setupMatrices();
		
		while (!Display.isCloseRequested()) {
			// Do a single loop (logic/render)
			this.loopCycle();
			
			// Force a maximum FPS of about 60
			Display.sync(60);
			// Let the CPU synchronize with the GPU if GPU is tagging behind
			Display.update();
		}
		
		this.destroyOpenGL();
	}

	private void setupMatrices() {
		
		matrix44Buffer = BufferUtils.createFloatBuffer(16);
	}

	private void setupTextures() {
		texIds[0] = this.bindPNGTexture("/assets/textures/techtrends.png", GL13.GL_TEXTURE0);
		texIds[1] = this.bindPNGTexture("/assets/textures/techtrends-favicon.png", GL13.GL_TEXTURE0);
		
		this.exitOnGLError("setupTexture");
	}

	private void setupOpenGL() {
		// Setup an OpenGL context with API version 3.2
		try {
			PixelFormat pixelFormat = new PixelFormat();
			ContextAttribs contextAtrributes = new ContextAttribs(3, 2)
				.withForwardCompatible(true)
				.withProfileCore(true);
			
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
		
		quad = new Quad();
		
		this.exitOnGLError("setupQuad");
		
		
		try {
			Model suzanne = OBJLoader.loadTexturedModel(new File("C:\\cube.obj"));
			entity = new Entity(suzanne);
			entity.setScale(new Vector3f(0.1f, 0.1f, 0.1f));
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.exitOnGLError("loadingSuzanne");
	}
	
	private void setupShaders() {
		// Load the vertex shader
		int vsId = this.loadShader("/assets/shaders/vertex.glsl", GL20.GL_VERTEX_SHADER);
		// Load the fragment shader
		int fsId = this.loadShader("/assets/shaders/fragment.glsl", GL20.GL_FRAGMENT_SHADER);
		
		// Create a new shader program that links both shaders
		pId = GL20.glCreateProgram();
		GL20.glAttachShader(pId, vsId);
		GL20.glAttachShader(pId, fsId);

		// Position information will be attribute 0
		GL20.glBindAttribLocation(pId, 0, "in_Position");
		// Color information will be attribute 1
		GL20.glBindAttribLocation(pId, 1, "in_Color");
		// Textute information will be attribute 2
		GL20.glBindAttribLocation(pId, 2, "in_TextureCoord");

		GL20.glLinkProgram(pId);
		GL20.glValidateProgram(pId);

		// Get matrices uniform locations
		camera.setProjectionMatrixLocation(GL20.glGetUniformLocation(pId,"projectionMatrix"));
		camera.setViewMatrixLocation(GL20.glGetUniformLocation(pId, "viewMatrix"));
		entity.setModelMatrixLocation(GL20.glGetUniformLocation(pId, "modelMatrix"));

		this.exitOnGLError("setupShaders");
	}
	
	private void update() {
		
		while(Keyboard.next()) {
			// Only listen to events where the key was pressed (down event)
			if (!Keyboard.getEventKeyState()) continue;
			
			// Switch textures depending on the key released
			int eventKey = Keyboard.getEventKey();
			switch (eventKey) {
			case Keyboard.KEY_1:
				textureSelector = 0;
				break;
			case Keyboard.KEY_2:
				textureSelector = 1;
				break;
			}
			
			camera.updateControls(eventKey);
		}
		
		//-- Update matrices
		// Reset view and model matrices
		camera.setViewMatrix(new Matrix4f());
		quad.setModelMatrix(new Matrix4f());
		
		camera.transform(camera.getViewMatrix());
		
		// Upload matrices to the uniform variables
		GL20.glUseProgram(pId);
		
		camera.flipBuffers(matrix44Buffer);
		quad.flipBuffers(matrix44Buffer);
		
		GL20.glUseProgram(0);
		
		this.exitOnGLError("logicCycle");
	}
	
	private void render() {
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		
		GL20.glUseProgram(pId);
		
		// Bind the texture
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texIds[textureSelector]);

		quad.draw();
		entity.draw();
		
		GL20.glUseProgram(0);
		
		this.exitOnGLError("renderCycle");
	}
	
	private void loopCycle() {
		// Update logic
		this.update();
		// Update rendered frame
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
		
		entity.destroy();
		quad.destroy();
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
	
	private int bindPNGTexture(String filename, int textureUnit) {

		TextureBuffer texture = Util.loadPNGTexture(filename, textureUnit);

		// Create a new texture object in memory and bind it
		int texId = GL11.glGenTextures();
		GL13.glActiveTexture(textureUnit);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
		
		// All RGB bytes are aligned to each other and each component is 1 byte
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		
		// Upload the texture data and generate mip maps (for scaling)
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, texture.getWidth(), texture.getHeight(), 0, 
				GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texture.getBuffer());
		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
		
		// Setup the ST coordinate system
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
		
		// Setup what to do when the texture has to be scaled
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, 
				GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, 
				GL11.GL_LINEAR_MIPMAP_LINEAR);

		this.exitOnGLError("loadPNGTexture");
		
		return texId;
	}
	
	
	private void exitOnGLError(String errorMessage) {
		int errorValue = GL11.glGetError();
		
		if (errorValue != GL11.GL_NO_ERROR) {
			String errorString = GLU.gluErrorString(errorValue);
			System.err.println("ERROR - " + errorMessage + ": " + errorString);
			
			if (Display.isCreated()) Display.destroy();
			System.exit(-1);
		}
	}
}