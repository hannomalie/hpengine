package renderer.rendertarget;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import texture.CubeMapArray;
import util.Util;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public class CubeMapArrayRenderTarget extends RenderTarget {

	private List<CubeMapArray> cubeMapArrays = new ArrayList<>();

	public CubeMapArrayRenderTarget(int width, int height, CubeMapArray... cubeMapArray) {
		this.width = width;
		this.height = height;
		this.clearR = 0;
		this.clearG = 0.3f;
		this.clearB = 0.3f;
		this.clearA = 0;
		for(CubeMapArray cma: cubeMapArray) {
			this.cubeMapArrays.add(cma);	
		}
		int colorBufferCount = cubeMapArrays.size();
		renderedTextures = new int[colorBufferCount];
		
		framebufferLocation = GL30.glGenFramebuffers();
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);
		IntBuffer scratchBuffer = BufferUtils.createIntBuffer(colorBufferCount);

		for (int i = 0; i < colorBufferCount; i++) {
//			GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrays.get(i).getTextureID());
//			GL30.glFramebufferTexture3D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0+i, GL12.GL_TEXTURE_3D, 0, 0, 0);
			GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0+i, cubeMapArrays.get(i).getTextureID(), 0, 0);
		    scratchBuffer.put(i, GL30.GL_COLOR_ATTACHMENT0+i);
		}
	    GL20.glDrawBuffers(scratchBuffer);

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
		setCubeMapFace(0, cubeMapIndex, faceIndex);
	}
	public void setCubeMapFace(int attachmentIndex, int cubeMapIndex, int faceIndex) {
		setCubeMapFace(attachmentIndex, attachmentIndex, cubeMapIndex, faceIndex);
	}

	public void setCubeMapFace(int cubeMapArrayListIndex, int attachmentIndex, int cubeMapIndex, int faceIndex) {
		GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0+attachmentIndex, cubeMapArrays.get(cubeMapArrayListIndex).getTextureID(), 0, 6*cubeMapIndex + faceIndex);
//		System.out.println("Setting cubemaparray number " + cubeMapArrayListIndex + " with texture id " + cubeMapArrays.get(cubeMapArrayListIndex).getTextureID() + " to attachment " + attachmentIndex + " with cubemap " + cubeMapIndex + " and face " + faceIndex);
	}
	public void resetAttachments() {
		for (int i = 0; i < cubeMapArrays.size(); i++) {
			GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0+i, 0, 0, 0);
		}
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
		return getCubeMapArray(0);
	}

	public CubeMapArray getCubeMapArray(int i) {
		return cubeMapArrays.get(i);
	}

	public void bindCubeMapFace(int unit, int attachment, int cubemapIndex, int sideIndex) {
		cubeMapArrays.get(attachment).bind(6*cubemapIndex + sideIndex, unit);
	}

}
