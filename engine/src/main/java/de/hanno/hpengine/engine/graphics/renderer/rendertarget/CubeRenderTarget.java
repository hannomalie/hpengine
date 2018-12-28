package de.hanno.hpengine.engine.graphics.renderer.rendertarget;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import de.hanno.hpengine.engine.backend.Backend;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget;
import org.lwjgl.opengl.*;
import de.hanno.hpengine.util.Util;

import org.lwjgl.BufferUtils;

import static org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP;
import static org.lwjgl.opengl.GL45.glTextureStorage2D;

public class CubeRenderTarget extends RenderTarget {

	public CubeRenderTarget(Backend engine, CubeRenderTargetBuilder builder) {
        super(engine.getGpuContext());
        this.width = builder.width;
		this.height = builder.height;
		this.clearR = builder.clearR;
		this.clearG = builder.clearR;
		this.clearB = builder.clearB;
		this.clearA = builder.clearA;
		int colorBufferCount = builder.colorAttachments.size();
		renderedTextures = new int[colorBufferCount];

		frameBuffer = engine.getGpuContext().genFrameBuffer();
		engine.getGpuContext().bindFrameBuffer(frameBuffer);

		scratchBuffer = BufferUtils.createIntBuffer(colorBufferCount);

		engine.getGpuContext().execute(() -> {
			for (int i = 0; i < colorBufferCount; i++) {
				ColorAttachmentDefinition currentAttachment = builder.colorAttachments.get(i);
				int internalFormat = currentAttachment.getInternalFormat();
				int maxMipMap = Util.calculateMipMapCount(Math.max(width, height));
				int cubeMap = GL11.glGenTextures();
				gpuContext.activeTexture(0);
				gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_CUBE_MAP, cubeMap);

				glTextureStorage2D(cubeMap, maxMipMap, internalFormat, width, height);

				GL11.glTexParameteri(GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
				GL11.glTexParameteri(GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, currentAttachment.getTextureFilter());
				GL11.glTexParameteri(GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
				GL11.glTexParameteri(GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
				GL11.glTexParameteri(GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_BASE_LEVEL, 0);
				GL11.glTexParameteri(GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_MAX_LEVEL, maxMipMap);

//				TODO: Only if mipmap filter...
				GL30.glGenerateMipmap(GlTextureTarget.TEXTURE_CUBE_MAP.glTarget);

//            TODO: Eliminate direct opengl calls
				GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0+i, cubeMap, 0);
				renderedTextures[i] = cubeMap;
				scratchBuffer.put(i, GL30.GL_COLOR_ATTACHMENT0+i);
			}

			if(builder.useDepthBuffer) {
				int depthCubeMap = engine.getTextureManager().getCubeMap(width, height, GL14.GL_DEPTH_COMPONENT24);
				GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, depthCubeMap, 0);
			}
			GL20.glDrawBuffers(scratchBuffer);

			//TODO: Make this more pretty
			int framebuffercheck = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
			if (framebuffercheck != GL30.GL_FRAMEBUFFER_COMPLETE) {
				System.err.println("CubeRenderTarget fucked up with " + framebuffercheck);
				System.exit(0);
			}
		});
	}
//
//	public void setCubeMapFace(int index) {
//		managerContext.getRenderer().getOpenGLContext().clearDepthBuffer();
//		managerContext.getRenderer().getOpenGLContext().bindTexture(TEXTURE_CUBE_MAP, cubeMap.getTextureId());
//		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + index, cubeMap.getTextureId(), 0);
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
