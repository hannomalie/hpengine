package de.hanno.hpengine.engine.graphics.renderer.drawstrategy;

import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer;
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer;
import de.hanno.hpengine.engine.graphics.buffer.StorageBuffer;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.PixelBufferObject;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetBuilder;
import de.hanno.hpengine.util.Util;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GBuffer {

	public static volatile int IMPORTANCE_SAMPLE_COUNT = 8;
	public static volatile boolean USE_COMPUTESHADER_FOR_REFLECTIONS = false;
	public static volatile boolean RENDER_PROBES_WITH_FIRST_BOUNCE = true;
	public static volatile boolean RENDER_PROBES_WITH_SECOND_BOUNCE = true;

    private RenderTarget gBuffer;
	private RenderTarget reflectionBuffer;
	private RenderTarget laBuffer;
	private RenderTarget finalBuffer;
	private RenderTarget halfScreenBuffer;

	public RenderTarget getgBuffer() {
		return gBuffer;
	}

	private FloatBuffer identityMatrixBuffer = BufferUtils.createFloatBuffer(16);

	private int fullScreenMipmapCount;

	private ByteBuffer vec4Buffer = BufferUtils.createByteBuffer(4*4).order(ByteOrder.nativeOrder());
	private FloatBuffer fBuffer = vec4Buffer.asFloatBuffer();
	private float[] onePixel = new float[4];

	private PixelBufferObject pixelBufferObject;
	
	private GPUBuffer storageBuffer;

	private final int exposureIndex = 0;

    public GBuffer(GpuContext gpuContext) {

		gBuffer = new RenderTargetBuilder<RenderTargetBuilder, RenderTarget>(gpuContext).setWidth(Config.getInstance().getWidth()).setHeight(Config.getInstance().getHeight())
				.add(4, new ColorAttachmentDefinition().setInternalFormat(GL30.GL_RGBA16F))
				.add(1, new ColorAttachmentDefinition().setInternalFormat(GL30.GL_RGBA32F))
				.add(1, new ColorAttachmentDefinition().setInternalFormat(GL30.GL_RGBA16F))
				.build();
		reflectionBuffer = new RenderTargetBuilder<RenderTargetBuilder, RenderTarget>(gpuContext).setWidth(Config.getInstance().getWidth()).setHeight(Config.getInstance().getHeight())
						.add(2, new ColorAttachmentDefinition()
								.setInternalFormat(GL30.GL_RGBA16F)
								.setTextureFilter(GL11.GL_LINEAR))
						.setClearRGBA(0, 0, 0, 0)
						.build();
		laBuffer = new RenderTargetBuilder<RenderTargetBuilder, RenderTarget>(gpuContext).setWidth(Config.getInstance().getWidth())
						.setHeight(Config.getInstance().getHeight())
						.add(2, new ColorAttachmentDefinition()
                                .setInternalFormat(GL30.GL_RGBA16F)
                                .setTextureFilter(GL11.GL_LINEAR))
						.build();
		finalBuffer = new RenderTargetBuilder<RenderTargetBuilder, RenderTarget>(gpuContext).setWidth(Config.getInstance().getWidth()).setHeight(Config.getInstance().getHeight())
						.add(new ColorAttachmentDefinition()
								.setInternalFormat(GL11.GL_RGBA8))
						.build();
		halfScreenBuffer = new RenderTargetBuilder<RenderTargetBuilder, RenderTarget>(gpuContext).setWidth(Config.getInstance().getWidth() / 2).setHeight(Config.getInstance().getHeight() / 2)
						.add(new ColorAttachmentDefinition()
								.setInternalFormat(GL30.GL_RGBA16F)
                                .setTextureFilter(GL11.GL_LINEAR_MIPMAP_LINEAR))
						.build();
		new Matrix4f().get(identityMatrixBuffer);
		identityMatrixBuffer.rewind();

		fullScreenMipmapCount = Util.calculateMipMapCount(Math.max(Config.getInstance().getWidth(), Config.getInstance().getHeight()));
		pixelBufferObject = new PixelBufferObject(gpuContext, 1, 1);
		
         storageBuffer = new PersistentMappedBuffer(gpuContext, 4*8);//new StorageBuffer(16);
         storageBuffer.putValues(1f,-1f,0f,1f);

        GpuContext.exitOnGLError("grid de.hanno.hpengine.texture creation");
	}
	
	public int getLightAccumulationMapOneId() {
		return laBuffer.getRenderedTexture(0);
	}
	public int getAmbientOcclusionMapId() {
		return laBuffer.getRenderedTexture(1);
	}
	public int getPositionMap() {
		return gBuffer.getRenderedTexture(0);
	}
	public int getNormalMap() {
		return gBuffer.getRenderedTexture(1);
	}
	public int getColorReflectivenessMap() {
		return gBuffer.getRenderedTexture(2);
	}
	public int getMotionMap() {
		return gBuffer.getRenderedTexture(3);
	}
	public int getVisibilityMap() {
		return gBuffer.getRenderedTexture(4);
	}

	public int getFinalMap() {
		return finalBuffer.getRenderedTexture(0);
	}

	public GPUBuffer getStorageBuffer() {
		return storageBuffer;
	}

	public void setStorageBuffer(StorageBuffer storageBuffer) {
		this.storageBuffer = storageBuffer;
	}

	public void use(boolean clear) {
		gBuffer.use(clear);
	}

	public RenderTarget getLightAccumulationBuffer() {
		return laBuffer;
	}

	public int getDepthBufferTexture() {
		return gBuffer.getDepthBufferTexture();
	}

	public RenderTarget getReflectionBuffer() {
		return reflectionBuffer;
	}

	public RenderTarget getHalfScreenBuffer() {
		return halfScreenBuffer;
	}

	public RenderTarget getFinalBuffer() {
		return finalBuffer;
	}

	public int getFullScreenMipmapCount() {
		return fullScreenMipmapCount;
	}

	public int getAmbientOcclusionScatteringMap() {
		return halfScreenBuffer.getRenderedTexture(0);
	}

	public int getReflectionMap() {
		return reflectionBuffer.getRenderedTexture(0);
	}

	public int getRefractedMap() {
		return reflectionBuffer.getRenderedTexture(1);
	}

	public RenderTarget getlaBuffer() {
		return laBuffer;
	}

}
