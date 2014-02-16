package main;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import main.util.Util;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

public class RenderTarget {
	private int framebufferLocation;
	private int depthbufferLocation;
	private int renderedTexture;
	private int width;
	private int height;
	private float clearR;
	private float clearG;
	private float clearB;
	private float clearA;

	public RenderTarget(int width, int height) {
		this(width, height, GL11.GL_RGB, 0.4f, 0.4f, 0.4f, 0f);
	}
	public RenderTarget(int width, int height, int internalFormat) {
		this(width, height, internalFormat, 0.4f, 0.4f, 0.4f, 0f);
	}

	public RenderTarget(int width, int height, float clearR, float clearG, float clearB, float clearA) {
		this(width, height, GL11.GL_RGB, clearR, clearG, clearB, clearA);
	}
	
	public RenderTarget(int width, int height, int internalFormat, float clearR, float clearG, float clearB, float clearA) {
		this.width = width;
		this.height = height;
		this.clearR = clearR;
		this.clearG = clearG;
		this.clearB = clearB;
		this.clearA = clearA;
		
		// create a frame and color buffer
		framebufferLocation = GL30.glGenFramebuffers();
		depthbufferLocation = GL30.glGenRenderbuffers();
		renderedTexture = GL11.glGenTextures();
		
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);
		
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, renderedTexture);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, BufferUtils.createFloatBuffer(width * height * 4));
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);

		GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthbufferLocation);
		GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_DEPTH_COMPONENT, width, height);
		GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthbufferLocation);
		
		GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, renderedTexture, 0);
		GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);
		
		//TODO: Make this more pretty
		int framebuffercheck = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
		if (framebuffercheck != GL30.GL_FRAMEBUFFER_COMPLETE) {
			System.exit(0);
		}
	}
	
	public void use(boolean clear) {
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);
		GL11.glViewport(0, 0, width, height);
		if (clear) {
			GL11.glClearColor(clearR,clearG,clearB,clearA);
			GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		}
	}
	
	public void unuse() {
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
	}

	public int getRenderedTexture() {
		return renderedTexture;
	}
	public int getDepthBufferTexture() {
		return depthbufferLocation;
	}

	public void setRenderedTexture(int renderedTexture) {
		this.renderedTexture = renderedTexture;
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

	private void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	private void setHeight(int height) {
		this.height = height;
	}
}
