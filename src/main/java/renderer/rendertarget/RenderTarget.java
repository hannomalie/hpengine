package renderer.rendertarget;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import util.Util;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

/**
 * @author Hanno
 *
 */
public class RenderTarget {
	protected int framebufferLocation;
	protected int depthbufferLocation;
	protected int[] renderedTextures;
	protected int width;
	protected int height;
	protected float clearR;
	protected float clearG;
	protected float clearB;
	protected float clearA;
	private int internalFormat;
	private int textureFilter;

	protected RenderTarget() {}
	
	public RenderTarget(int width, int height) {
		this(width, height, GL11.GL_RGB, 0.4f, 0.4f, 0.4f, 0f, GL11.GL_LINEAR, 1);
	}
	public RenderTarget(int width, int height, int internalFormat) {
		this(width, height, internalFormat, 0.4f, 0.4f, 0.4f, 0f, GL11.GL_LINEAR, 1);
	}
	public RenderTarget(int width, int height, int internalFormat, int colorBufferCount) {
		this(width, height, internalFormat, 0.4f, 0.4f, 0.4f, 0f, GL11.GL_LINEAR, colorBufferCount);
	}

	public RenderTarget(int width, int height, float clearR, float clearG, float clearB, float clearA) {
		this(width, height, GL11.GL_RGB, clearR, clearG, clearB, clearA, GL11.GL_LINEAR, 1);
	}
	
	public RenderTarget(int width, int height, int internalFormat, float clearR, float clearG, float clearB, float clearA, int textureFilter, int colorBufferCount) {
		this.width = width;
		this.height = height;
		this.clearR = clearR;
		this.clearG = clearG;
		this.clearB = clearB;
		this.clearA = clearA;
		this.internalFormat = internalFormat;
		this.textureFilter = textureFilter;
		
		renderedTextures = new int[colorBufferCount];
		
		// create a frame and color buffer
		framebufferLocation = GL30.glGenFramebuffers();
		depthbufferLocation = GL30.glGenRenderbuffers();

		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);
		initColorAttachments(width, height, internalFormat, textureFilter, colorBufferCount);
		
		GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthbufferLocation);
		GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_DEPTH_COMPONENT, width, height);
		GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthbufferLocation);
		
//		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);
		
		//TODO: Make this more pretty
		int framebuffercheck = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
		if (framebuffercheck != GL30.GL_FRAMEBUFFER_COMPLETE) {
			System.out.println("RenderTarget fucked up");
			System.out.println(org.lwjgl.opengl.Util.translateGLErrorString(GL11.glGetError()));
			System.exit(0);
		}
		GL11.glClearColor(clearR,clearG,clearB,clearA);
	}

	private void initColorAttachments(int width, int height, int internalFormat, int textureFilter, int colorBufferCount) {
		IntBuffer scratchBuffer = BufferUtils.createIntBuffer(colorBufferCount);
		for (int i = 0; i < colorBufferCount; i++) {
			int renderedTextureTemp = GL11.glGenTextures();
			
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, renderedTextureTemp);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);

			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, textureFilter);

			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			FloatBuffer borderColorBuffer = BufferUtils.createFloatBuffer(4);
			float[] borderColors = new float[] {0,0,0,1};
			borderColorBuffer.put(borderColors);
			borderColorBuffer.rewind();
			GL11.glTexParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_BORDER_COLOR, borderColorBuffer);
			GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + i, renderedTextureTemp, 0);
		    scratchBuffer.put(i, GL30.GL_COLOR_ATTACHMENT0+i);
			renderedTextures[i] = renderedTextureTemp;
		}
	    GL20.glDrawBuffers(scratchBuffer);
	}
	
	public void use(boolean clear) {
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);
//		GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthbufferLocation);
		GL11.glViewport(0, 0, width, height);
		if (clear) {
			GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		}
	}

	public void setTargetTexture(int textureID, int index) {
		setTargetTexture(textureID, index, 0);
	}
	public void setTargetTexture(int textureID, int index, int mipMapLevel) {
		GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + index, textureID, mipMapLevel);
	}

	/**
	 * @param attachmentIndex the attachment point index
	 * @param textureId the id of the cubemap texture
	 * @param index the index of the cubemap face, from 0 to 5
	 * @param mipmap the mipmap level that should be bound
	 */
	public void setCubeMapFace(int attachmentIndex, int textureId, int index, int mipmap) {
		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + attachmentIndex, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + index, textureId, mipmap);
	}
	
	public void unuse() {
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
	}

	public int getRenderedTexture(int index) {
		return renderedTextures[index];
	}
	public int getDepthBufferTexture() {
		return depthbufferLocation;
	}

	public void setRenderedTexture(int renderedTexture, int index) {
		this.renderedTextures[index] = renderedTexture;
	}

	public void saveBuffer(String path) {
		Util.saveImage(getBuffer(), path);
	}
	public void saveDepthBuffer(String path) {
		Util.saveImage(getDepthBuffer(), path);
	}

	public BufferedImage getBuffer() {
		return Util.toImage(getTargetTexturedata(), width, height);
	}
	public BufferedImage getDepthBuffer() {
		return Util.toImage(getDepthTexturedata(), width, height);
	}

	public ByteBuffer getTargetTexturedata() {
		ByteBuffer pixels = BufferUtils.createByteBuffer(width*height*4);
		GL11.glReadPixels(0, 0, width, height, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, pixels);

		return pixels;
	}
	
	
	public ByteBuffer getDepthTexturedata() {
		ByteBuffer pixels = BufferUtils.createByteBuffer(width*height*4);
		GL11.glReadPixels(0, 0, width, height, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, pixels);

		return pixels;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}
	public int getRenderedTexture() {
		return getRenderedTexture(0);
	}

	public void resizeTextures() {
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);
		
		IntBuffer scratchBuffer = BufferUtils.createIntBuffer(renderedTextures.length);
		for (int i = 0; i < renderedTextures.length; i++) {
			int renderedTextureTemp = renderedTextures[i];
			
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, renderedTextureTemp);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);

			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, textureFilter);

			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			FloatBuffer borderColorBuffer = BufferUtils.createFloatBuffer(4);
			float[] borderColors = new float[] {0,0,0,1};
			borderColorBuffer.put(borderColors);
			borderColorBuffer.rewind();
			GL11.glTexParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_BORDER_COLOR, borderColorBuffer);
			GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + i, renderedTextureTemp, 0);
		    scratchBuffer.put(i, GL30.GL_COLOR_ATTACHMENT0+i);
			renderedTextures[i] = renderedTextureTemp;
		}
	    GL20.glDrawBuffers(scratchBuffer);
	}

	public int getFrameBufferLocation() {
		return framebufferLocation;
	}
}