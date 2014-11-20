package main.renderer.rendertarget;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Random;

import main.texture.CubeMap;
import main.util.Util;
import main.util.stopwatch.GPUProfiler;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public class CubeRenderTarget extends RenderTarget {

	private CubeMap cubeMap;

	public CubeRenderTarget(int width, int height, CubeMap cubeMap) {
		this.width = width;
		this.height = height;
		this.clearR = 0;
		this.clearG = 0.3f;
		this.clearB = 0.3f;
		this.clearA = 0;
		this.cubeMap = cubeMap;
		int colorBufferCount = 1;
		renderedTextures = new int[colorBufferCount];
		
		framebufferLocation = GL30.glGenFramebuffers();
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);
		
//		GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cubeMap.getTextureID());

//		IntBuffer scratchBuffer = BufferUtils.createIntBuffer(colorBufferCount);
		for (int i = 0; i < colorBufferCount; i++) {

			GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, cubeMap.getTextureID(), 0);
//		    scratchBuffer.put(i, GL30.GL_COLOR_ATTACHMENT0+i);
		}
//	    GL20.glDrawBuffers(scratchBuffer);
		
		depthbufferLocation = GL30.glGenRenderbuffers();
		GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthbufferLocation);
		GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_DEPTH_COMPONENT, width, height);
		GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthbufferLocation);
		
//		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);
		
		//TODO: Make this more pretty
		int framebuffercheck = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
		if (framebuffercheck != GL30.GL_FRAMEBUFFER_COMPLETE) {
			System.out.println("CubeRenderTarget fucked up with " + framebuffercheck);
			System.out.println(org.lwjgl.opengl.Util.translateGLErrorString(GL11.glGetError()));
			System.exit(0);
		}
	}
	
	public void setCubeMapFace(int index) {
		GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cubeMap.getTextureID());
		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + index, cubeMap.getTextureID(), 0);
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

	public int getRenderedTexture() {
		return cubeMap.getTextureID();
	}
	
	public CubeMap getCubeMap() {
		return cubeMap;
	}

	public void setCubeMap(CubeMap cubeMap) {
		this.cubeMap = cubeMap;
	}
}
