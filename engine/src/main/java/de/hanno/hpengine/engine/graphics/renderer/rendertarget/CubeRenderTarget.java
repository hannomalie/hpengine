package de.hanno.hpengine.engine.graphics.renderer.rendertarget;

import de.hanno.hpengine.engine.backend.Backend;
import de.hanno.hpengine.engine.graphics.renderer.constants.MagFilter;
import de.hanno.hpengine.engine.graphics.renderer.constants.MinFilter;
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig;
import de.hanno.hpengine.engine.model.texture.SimpleCubeMap;
import de.hanno.hpengine.engine.model.texture.TextureDimension;
import org.lwjgl.opengl.GL14;

import static de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetKt.toCubeMaps;
import static org.lwjgl.opengl.GL11.GL_REPEAT;

public class CubeRenderTarget extends RenderTarget {

	static DepthBuffer getDepthBufferOrNull(Backend engine, CubeRenderTargetBuilder builder) {
		if(builder.useDepthBuffer) {
			SimpleCubeMap depthCubeMap = SimpleCubeMap.Companion.invoke(
					engine.getGpuContext(),
					new TextureDimension(builder.width, builder.height),
					new TextureFilterConfig(MinFilter.NEAREST, MagFilter.NEAREST),
					GL14.GL_DEPTH_COMPONENT24, GL_REPEAT);

			return new DepthBuffer(depthCubeMap);
		} else return null;
	}

	public CubeRenderTarget(Backend engine, CubeRenderTargetBuilder builder) {
        super(
			FrameBuffer.Companion.invoke(engine.getGpuContext(), getDepthBufferOrNull(engine, builder)),
			builder.width,
			builder.height,
			toCubeMaps(builder.colorAttachments, engine.getGpuContext(), builder.width, builder.height),
			"CubeRenderTarget",
			builder.getClear()
		);
		initialize(engine.getGpuContext());
	}
}
