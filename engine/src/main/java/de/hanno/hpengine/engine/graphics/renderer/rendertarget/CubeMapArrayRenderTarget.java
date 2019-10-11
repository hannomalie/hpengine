package de.hanno.hpengine.engine.graphics.renderer.rendertarget;

import de.hanno.hpengine.engine.backend.OpenGl;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.constants.MagFilter;
import de.hanno.hpengine.engine.graphics.renderer.constants.MinFilter;
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig;
import de.hanno.hpengine.engine.model.texture.CubeMapArray;
import de.hanno.hpengine.engine.model.texture.CubeMap;
import de.hanno.hpengine.engine.model.texture.Texture2D;
import de.hanno.hpengine.engine.model.texture.TextureDimension;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static de.hanno.hpengine.engine.model.texture.TextureImplsKt.createView;
import static org.lwjgl.opengl.GL11.GL_REPEAT;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24;

public class CubeMapArrayRenderTarget extends RenderTarget<CubeMapArray> {

	public List<CubeMap> cubeMapViews;
	public List<Texture2D> cubeMapFaceViews;

	static DepthBuffer<CubeMapArray> getDepthBuffer(GpuContext gpuContext, int width, int height, int depth) {
		TextureDimension dimension = new TextureDimension(width, height, depth);
		TextureFilterConfig filterConfig = new TextureFilterConfig(MinFilter.NEAREST, MagFilter.NEAREST);
		return new DepthBuffer<>(CubeMapArray.Companion.invoke(gpuContext, dimension, filterConfig, GL_DEPTH_COMPONENT24, GL_REPEAT));
	}

	static List<CubeMapArray> toList(CubeMapArray... arrays) {
		return Arrays.asList(arrays);
	}

	public CubeMapArrayRenderTarget(GpuContext<OpenGl> gpuContext, int width, int height, Vector4f clear, CubeMapArray... cubeMapArray) {
		super(
			FrameBuffer.Companion.invoke(gpuContext, getDepthBuffer(gpuContext, width, height, cubeMapArray.length)),
			width,
			height,
			toList(cubeMapArray),
			"CubeMapArrayRenderTarget",
			clear
		);
		cubeMapViews = new ArrayList<>();
		cubeMapFaceViews = new ArrayList<>();

		initialize(gpuContext);

		for(int cubeMapArrayIndex = 0; cubeMapArrayIndex < cubeMapArray.length; cubeMapArrayIndex++) {
			CubeMapArray cma = cubeMapArray[cubeMapArrayIndex];
			gpuContext.execute("createViews for " + cubeMapArrayIndex, () -> {
				gpuContext.bindTexture(cma);
				for(int cubeMapIndex = 0; cubeMapIndex < cubeMapArray.length; cubeMapIndex++) {
					CubeMap cubeMapView = createView(cma, gpuContext, cubeMapIndex);
					cubeMapViews.add(cubeMapView);
					for(int faceIndex = 0; faceIndex < 6; faceIndex++) {
						cubeMapFaceViews.add(createView(cma, gpuContext, cubeMapIndex, faceIndex));
					}
				}
			});
		}
	}

	public void setCubeMapFace(int attachmentIndex, int cubeMapIndex, int faceIndex) {
		setCubeMapFace(attachmentIndex, attachmentIndex, cubeMapIndex, faceIndex);
	}

	public void setCubeMapFace(int cubeMapArrayListIndex, int attachmentIndex, int cubeMapIndex, int faceIndex) {
		GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0+attachmentIndex, getTextures().get(cubeMapArrayListIndex).getId(), 0, 6*cubeMapIndex + faceIndex);
	}
	public void resetAttachments() {
		for (int i = 0; i < getTextures().size(); i++) {
			GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0+i, 0, 0, 0);
		}
	}
	
	public CubeMapArray getCubeMapArray(int i) {
		return getTextures().get(i);
	}

}
