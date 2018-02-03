package de.hanno.hpengine.engine.graphics.renderer.rendertarget;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import de.hanno.hpengine.engine.Engine;
import org.lwjgl.opengl.*;
import de.hanno.hpengine.util.Util;

import org.lwjgl.BufferUtils;

public class CubeRenderTarget extends RenderTarget {

	public CubeRenderTarget(CubeRenderTargetBuilder builder) {
		this.width = builder.width;
		this.height = builder.height;
		this.clearR = builder.clearR;
		this.clearG = builder.clearR;
		this.clearB = builder.clearB;
		this.clearA = builder.clearA;
		int colorBufferCount = builder.colorAttachments.size();
		renderedTextures = new int[colorBufferCount];

		framebufferLocation = GL30.glGenFramebuffers();
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);

		scratchBuffer = BufferUtils.createIntBuffer(colorBufferCount);

		for (int i = 0; i < colorBufferCount; i++) {
			int internalFormat = builder.colorAttachments.get(i).internalFormat;
            int cubeMap = Engine.getInstance().getTextureFactory().getCubeMap(width, height, internalFormat);
			GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0+i, cubeMap, 0);
			renderedTextures[i] = cubeMap;
			scratchBuffer.put(i, GL30.GL_COLOR_ATTACHMENT0+i);
		}

		if(builder.useDepthBuffer) {
            int depthCubeMap = Engine.getInstance().getTextureFactory().getCubeMap(width, height, GL14.GL_DEPTH_COMPONENT24);
			GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, depthCubeMap, 0);
		}
		GL20.glDrawBuffers(scratchBuffer);

		//TODO: Make this more pretty
		int framebuffercheck = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
		if (framebuffercheck != GL30.GL_FRAMEBUFFER_COMPLETE) {
			System.err.println("CubeRenderTarget fucked up with " + framebuffercheck);
			System.exit(0);
		}
	}
//
//	public void setCubeMapFace(int index) {
//		Engine.getInstance().getRenderer().getOpenGLContext().clearDepthBuffer();
//		Engine.getInstance().getRenderer().getOpenGLContext().bindTexture(TEXTURE_CUBE_MAP, cubeMap.getTextureID());
//		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + index, cubeMap.getTextureID(), 0);
//	}

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
		return renderedTextures[0];
	}
}
