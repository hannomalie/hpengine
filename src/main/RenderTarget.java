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
	
	public RenderTarget(int width, int height) {
		this.width = width;
		this.height = height;
		
		// create a frame and color buffer
		framebufferLocation = GL30.glGenFramebuffers();
		depthbufferLocation = GL30.glGenRenderbuffers();
		renderedTexture = GL11.glGenTextures();
		
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);
		
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, renderedTexture);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, BufferUtils.createFloatBuffer(width * height * 4));
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);

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
	
	public void use() {
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);
		GL11.glViewport(0, 0, width, height);
		//TODO: pushattrib viewport bit bla
		GL11.glClearColor(0.4f, 0.6f, 0.9f, 0f);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
	}
	
	public void unuse() {
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		//TODO: gl pop attrib
//		GL11.glClearColor(0.0f, 0.0f, 0.9f, 0f);
//		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
	}

	public int getRenderedTexture() {
		return renderedTexture;
	}

	public void setRenderedTexture(int renderedTexture) {
		this.renderedTexture = renderedTexture;
	}
	
	public void saveBuffer(String path) {
		Util.saveImage(getBuffer(), path);
	}
	
	public BufferedImage getBuffer() {
		return Util.toImage(getTargetTexturedata(), width, height);
	}

	public ByteBuffer getTargetTexturedata() {
		ByteBuffer pixels = BufferUtils.createByteBuffer(width*height*4);
		GL11.glReadPixels(0, 0, width, height, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, pixels);

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
