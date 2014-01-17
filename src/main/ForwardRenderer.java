package main;

import java.nio.FloatBuffer;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL41;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.util.glu.GLU;

import main.util.Util;

public class ForwardRenderer {
	public static final int WIDTH = 800;
	public static final int HEIGHT = 600;
	static float G_QUAD_VERTEX_BUFFER_DATA[] = {
		    -1.0f, -1.0f, 0.0f,
		    1.0f, -1.0f, 0.0f,
		    -1.0f,  1.0f, 0.0f,
		    -1.0f,  1.0f, 0.0f,
		    1.0f, -1.0f, 0.0f,
		    1.0f,  1.0f, 0.0f,
		};
	FloatBuffer quadVerticesFloatBuffer = BufferUtils.createFloatBuffer(18*4);
	
	private int fps;
	private long lastFPS;
	private long lastFrame;
	
	private static int modelMatrixLocation;
	private static int materialProgramId = 0;
	private int textureSelector = 0;
	private Camera camera = new Camera();
	private FloatBuffer matrix44Buffer = null;
	private int framebufferLocation;
	private int renderTextureLocation;
	private int depthbufferLocation;
	private int quadVertexArray;
	private int quadVertexBuffer;
	private int quadVertexShaderId;
	private int quadFragmentShaderId;
	private int passthroughProgram;
	private int renderedTexture;
	
	
	public ForwardRenderer() {
		setupOpenGL();
		setupShaders();
		setupMatrices();
		getDelta();
		lastFPS = Util.getTime();
		Mouse.setCursorPosition(WIDTH/2, HEIGHT/2);
	}

	private void setupOpenGL() {
		try {
			PixelFormat pixelFormat = new PixelFormat();
			ContextAttribs contextAtrributes = new ContextAttribs(4, 2)
				.withForwardCompatible(true);
//				.withProfileCore(true);
			
			Display.setDisplayMode(new DisplayMode(WIDTH, HEIGHT));
			Display.setTitle("ForwardRenderer");
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

		// create a frame and color buffer
		framebufferLocation = GL30.glGenFramebuffers();
		depthbufferLocation = GL30.glGenRenderbuffers();
		renderedTexture = GL11.glGenTextures();
		
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, renderedTexture);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, WIDTH, HEIGHT, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, BufferUtils.createFloatBuffer(WIDTH * HEIGHT * 4));
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);

		GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthbufferLocation);
		GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT32F, HEIGHT, WIDTH);
		GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthbufferLocation);
		
		GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, renderedTexture, 0);
		GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
//		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);

		// Buffers, shaders etc. for rendertoquad
		quadVertexArray = GL30.glGenVertexArrays();
		quadVertexBuffer = GL15.glGenBuffers();
		quadVertexShaderId = loadShader("/assets/shaders/passthrough_vertex.glsl", GL20.GL_VERTEX_SHADER);
		quadFragmentShaderId = loadShader("/assets/shaders/simpletexture_fragment.glsl", GL20.GL_FRAGMENT_SHADER);

		passthroughProgram = GL20.glCreateProgram();
		GL20.glAttachShader(passthroughProgram, quadVertexShaderId);
		GL20.glAttachShader(passthroughProgram, quadFragmentShaderId);
		GL20.glLinkProgram(passthroughProgram);
		GL20.glValidateProgram(passthroughProgram);
		renderTextureLocation = GL20.glGetUniformLocation(passthroughProgram, "renderedTexture");
		
		for (int i = 0; i < G_QUAD_VERTEX_BUFFER_DATA.length; i++) {
			quadVerticesFloatBuffer.put(G_QUAD_VERTEX_BUFFER_DATA[i]);
		}
		
		this.exitOnGLError("setupOpenGL");
	}


	private void setupMatrices() {
		matrix44Buffer = BufferUtils.createFloatBuffer(16);
	}
	
	private void setupShaders() {
		int vsId = this.loadShader("/assets/shaders/vertex.glsl", GL20.GL_VERTEX_SHADER);
		int fsId = this.loadShader("/assets/shaders/fragment.glsl", GL20.GL_FRAGMENT_SHADER);
		
		materialProgramId = GL20.glCreateProgram();
		GL20.glAttachShader(materialProgramId, vsId);
		GL20.glAttachShader(materialProgramId, fsId);

		GL20.glBindAttribLocation(materialProgramId, 0, "in_Position");
		GL20.glBindAttribLocation(materialProgramId, 1, "in_Color");
		GL20.glBindAttribLocation(materialProgramId, 2, "in_TextureCoord");
		GL20.glBindAttribLocation(materialProgramId, 3, "in_Normal");
		GL20.glBindAttribLocation(materialProgramId, 4, "in_Binormal");
		GL20.glBindAttribLocation(materialProgramId, 5, "in_Tangent");

		GL20.glLinkProgram(materialProgramId);
		GL20.glValidateProgram(materialProgramId);

		camera.setProjectionMatrixLocation(GL20.glGetUniformLocation(materialProgramId,"projectionMatrix"));
		camera.setViewMatrixLocation(GL20.glGetUniformLocation(materialProgramId, "viewMatrix"));
		ForwardRenderer.modelMatrixLocation = GL20.glGetUniformLocation(materialProgramId, "modelMatrix");
		World.lightPositionLocation = GL20.glGetUniformLocation(materialProgramId, "lightPosition");
		World.useParallaxLocation = GL20.glGetUniformLocation(materialProgramId, "useParallax");

		GL20.glUseProgram(materialProgramId);
		
		this.exitOnGLError("setupShaders");
	}

	public int getDelta() {
		long time = Util.getTime();
		int delta = (int) (time - lastFrame);
		lastFrame = time;
		 
		return delta;
	}

	public static int getModelMatrixLocation() {
		return modelMatrixLocation;
	}

	public void setModelMatrixLocation(int modelMatrixLocation) {
		this.modelMatrixLocation = modelMatrixLocation;
	}

	public int getpId() {
		return materialProgramId;
	}

	public void setpId(int pId) {
		this.materialProgramId = pId;
	}

	public int getTextureSelector() {
		return textureSelector;
	}

	public void setTextureSelector(int textureSelector) {
		this.textureSelector = textureSelector;
	}

	public Camera getCamera() {
		return camera;
	}

	public void setCamera(Camera camera) {
		this.camera = camera;
	}

	public void update() {
		camera.updateControls();
		camera.transform();
		updateFPS();
		GL20.glUseProgram(materialProgramId);
		
		camera.flipBuffers(matrix44Buffer);
	}
	
	public void draw(List<IEntity> entities) {

		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

		GL20.glUseProgram(materialProgramId);
		ForwardRenderer.exitOnGLError("useProgram in render");
		
//		quad.draw();
		for (IEntity entity: entities) {
			entity.draw();
		}
		
		drawToQuad();
	}
	
	private void drawToQuad() {
//		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0); 
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		
		GL20.glUseProgram(passthroughProgram);

//		System.out.println("tex in quadshader is " + renderedTexture);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, renderedTexture);
		GL20.glUniform1i(renderTextureLocation, 0);
		
		GL30.glBindVertexArray(quadVertexArray);
		
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVertexBuffer);
		
		quadVerticesFloatBuffer.flip();
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, quadVerticesFloatBuffer, GL15.GL_DYNAMIC_DRAW);

//		System.out.println(GL20.glGetProgramInfoLog(passthroughProgram, 10000));
		exitOnGLError("useprogram in drawToQuad");

		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
		exitOnGLError("glDrawArrays in drawToQuad");
	}

	public void destroy() {
		destroyOpenGL();
	}

	private void destroyOpenGL() {
		// Delete the shaders
		GL20.glUseProgram(0);
		GL20.glDeleteProgram(materialProgramId);

		ForwardRenderer.exitOnGLError("destroyOpenGL");
		
		Display.destroy();
	}

	public int loadShader(String filename, int type) {
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
		
		ForwardRenderer.exitOnGLError("loadShader");
		
		return shaderID;
	}

	
	public static int loadTextureToGL(String filename, int program, String name, int index) {

		TextureBuffer texture = Util.loadPNGTexture(filename, program);

		int texId = GL11.glGenTextures();
		//System.out.println("Have " + name + " " + texId);
		//GL13.glActiveTexture(GL13.GL_TEXTURE0 + index);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + Material.textureIndex);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, texture.getWidth(), texture.getHeight(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texture.getBuffer());
		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
		
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);

		GL20.glUniform1i(GL20.glGetUniformLocation(program, name), index);
		ForwardRenderer.exitOnGLError("loadPNGTexture");
		Material.textureIndex++;

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

	public static void exitOnGLError(String errorMessage) {
		int errorValue = GL11.glGetError();
		
		if (errorValue != GL11.GL_NO_ERROR) {
			String errorString = GLU.gluErrorString(errorValue);
			System.err.println("ERROR - " + errorMessage + ": " + errorString);
			
			if (Display.isCreated()) Display.destroy();
			System.exit(-1);
		}
	}

	public static int getMaterialProgramId() {
		return materialProgramId;
	}

	public static void setMaterialProgramId(int materialProgramId) {
		ForwardRenderer.materialProgramId = materialProgramId;
	}
}
