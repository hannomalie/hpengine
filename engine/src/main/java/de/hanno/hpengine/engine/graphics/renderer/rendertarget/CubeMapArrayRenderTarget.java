package de.hanno.hpengine.engine.graphics.renderer.rendertarget;

import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import de.hanno.hpengine.engine.model.texture.CubeMapArray;
import de.hanno.hpengine.util.Util;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class CubeMapArrayRenderTarget extends RenderTarget {
    private final ArrayList<long[]> handleLists;
    private List<CubeMapArray> cubeMapArrays = new ArrayList<>();

	public CubeMapArrayRenderTarget(int width, int height, int depth, CubeMapArray... cubeMapArray) {
		this.width = width;
		this.height = height;
		this.clearR = 0;
		this.clearG = 0.3f;
		this.clearB = 0.3f;
		this.clearA = 0;
        this.handleLists = new ArrayList<>(cubeMapArray.length);

		for(CubeMapArray cma: cubeMapArray) {
			this.cubeMapArrays.add(cma);
            long[] currentList = new long[cma.getCubemapCount()];
            handleLists.add(currentList);
            for(int cubemapIndex = 0; cubemapIndex < cma.getCubemapCount(); cubemapIndex++) {
                int cubeMapView = GraphicsContext.getInstance().genTextures();
                int finalCubemapIndex = cubemapIndex;
                GraphicsContext.getInstance().execute(() -> {
                    GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cma.getTextureID(),
                            cma.getInternalFormat(), 0, 1,
                            6 * finalCubemapIndex, 6);

                });
                long handle = GraphicsContext.getInstance().calculate(() ->ARBBindlessTexture.glGetTextureHandleARB(cubeMapView));
                currentList[cubemapIndex] = handle;
                GraphicsContext.getInstance().execute(() -> {
                    ARBBindlessTexture.glMakeTextureHandleResidentARB(handle);
                });
            }
		}
		int colorBufferCount = cubeMapArrays.size();
		renderedTextures = new int[colorBufferCount];

        GraphicsContext.getInstance().execute(() -> {
			framebufferLocation = GL30.glGenFramebuffers();
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);
			IntBuffer scratchBuffer = BufferUtils.createIntBuffer(colorBufferCount);

			for (int i = 0; i < colorBufferCount; i++) {
				GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + i, cubeMapArrays.get(i).getTextureID(), 0);
				scratchBuffer.put(i, GL30.GL_COLOR_ATTACHMENT0+i);
				renderedTextures[i] = cubeMapArrays.get(i).getTextureID();
			}
			GL20.glDrawBuffers(scratchBuffer);

            CubeMapArray depthCubeMapArray = new CubeMapArray(depth, GL11.GL_LINEAR, GL14.GL_DEPTH_COMPONENT24, width);
			int depthCubeMapArrayId = depthCubeMapArray.getTextureID();
			GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, depthCubeMapArrayId, 0);
			depthbufferLocation = depthCubeMapArray.getTextureID();

			//TODO: Make this more pretty
			int framebuffercheck = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
			if (framebuffercheck != GL30.GL_FRAMEBUFFER_COMPLETE) {
				System.err.println("CubeRenderTarget fucked up with " + framebuffercheck);
                new Exception().printStackTrace();
				System.exit(0);
			}
		});
	}

	public void setCubeMapFace(int cubeMapIndex, int faceIndex) {
		setCubeMapFace(0, cubeMapIndex, faceIndex);
	}
	public void setCubeMapFace(int attachmentIndex, int cubeMapIndex, int faceIndex) {
		setCubeMapFace(attachmentIndex, attachmentIndex, cubeMapIndex, faceIndex);
	}

	public void setCubeMapFace(int cubeMapArrayListIndex, int attachmentIndex, int cubeMapIndex, int faceIndex) {
		GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0+attachmentIndex, cubeMapArrays.get(cubeMapArrayListIndex).getTextureID(), 0, 6*cubeMapIndex + faceIndex);
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

    public ArrayList<long[]> getHandleLists() {
        return handleLists;
    }

}
