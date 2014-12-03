package main.renderer.rendertarget;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Random;

import main.texture.CubeMap;
import main.texture.CubeMapArray;
import main.util.Util;
import main.util.stopwatch.GPUProfiler;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
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

public class CubeMapArrayRenderTarget extends RenderTarget {

	private CubeMapArray cubeMapArray;

	public CubeMapArrayRenderTarget(int width, int height, CubeMapArray cubeMapArray) {
		this.width = width;
		this.height = height;
		this.clearR = 0;
		this.clearG = 0.3f;
		this.clearB = 0.3f;
		this.clearA = 0;
		this.cubeMapArray = cubeMapArray;
		int colorBufferCount = 1;
		renderedTextures = new int[colorBufferCount];
		
		framebufferLocation = GL30.glGenFramebuffers();
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);
		
//		GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cubeMap.getTextureID());

//		IntBuffer scratchBuffer = BufferUtils.createIntBuffer(colorBufferCount);
		for (int i = 0; i < colorBufferCount; i++) {

			GL30.glFramebufferTexture3D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL12.GL_TEXTURE_3D, 0, 0, 0);
			GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, cubeMapArray.getTextureID(), 0, 0);
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
	
	public void setCubeMapFace(int cubeMapIndex, int faceIndex) {
		GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
		GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, cubeMapArray.getTextureID(), 0, 6*cubeMapIndex + faceIndex);
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

	public CubeMapArray getCubeMapArray() {
		return cubeMapArray;
	}
}
