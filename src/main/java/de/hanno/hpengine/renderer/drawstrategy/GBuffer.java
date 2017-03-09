package de.hanno.hpengine.renderer.drawstrategy;

import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.*;
import de.hanno.hpengine.renderer.DeferredRenderer;
import de.hanno.hpengine.renderer.PixelBufferObject;
import de.hanno.hpengine.renderer.material.Material;
import de.hanno.hpengine.renderer.material.MaterialFactory;
import de.hanno.hpengine.renderer.rendertarget.ColorAttachmentDefinition;
import de.hanno.hpengine.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.renderer.rendertarget.RenderTargetBuilder;
import de.hanno.hpengine.shader.OpenGLBuffer;
import de.hanno.hpengine.shader.PersistentMappedBuffer;
import de.hanno.hpengine.shader.StorageBuffer;
import de.hanno.hpengine.util.Util;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import java.io.File;
import java.io.IOException;
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
	
	private FloatBuffer identityMatrixBuffer = BufferUtils.createFloatBuffer(16);

	private int fullScreenMipmapCount;

	private Model probeBox;
	private Entity probeBoxEntity;

	private ByteBuffer vec4Buffer = BufferUtils.createByteBuffer(4*4).order(ByteOrder.nativeOrder());
	private FloatBuffer fBuffer = vec4Buffer.asFloatBuffer();
	private float[] onePixel = new float[4];

	private PixelBufferObject pixelBufferObject;
	
	private OpenGLBuffer storageBuffer;

	private final int exposureIndex = 0;

    public GBuffer() {

        gBuffer = new RenderTargetBuilder().setWidth(Config.getInstance().getWidth()).setHeight(Config.getInstance().getHeight())
						.add(6, new ColorAttachmentDefinition().setInternalFormat(GL30.GL_RGBA16F))
						.build();
		reflectionBuffer = new RenderTargetBuilder().setWidth(Config.getInstance().getWidth()).setHeight(Config.getInstance().getHeight())
						.add(2, new ColorAttachmentDefinition()
								.setInternalFormat(GL30.GL_RGBA16F)
								.setTextureFilter(GL11.GL_LINEAR))
						.setClearRGBA(0, 0, 0, 0)
						.build();
		laBuffer = new RenderTargetBuilder().setWidth((int) (Config.getInstance().getWidth()))
						.setHeight((int) (Config.getInstance().getHeight()))
						.add(2, new ColorAttachmentDefinition()
                                .setInternalFormat(GL30.GL_RGBA16F)
                                .setTextureFilter(GL11.GL_LINEAR))
						.build();
		finalBuffer = new RenderTargetBuilder().setWidth(Config.getInstance().getWidth()).setHeight(Config.getInstance().getHeight())
						.add(new ColorAttachmentDefinition()
								.setInternalFormat(GL11.GL_RGBA8))
						.build();
		halfScreenBuffer = new RenderTargetBuilder().setWidth(Config.getInstance().getWidth() / 2).setHeight(Config.getInstance().getHeight() / 2)
						.add(new ColorAttachmentDefinition()
								.setInternalFormat(GL30.GL_RGBA16F)
                                .setTextureFilter(GL11.GL_LINEAR_MIPMAP_LINEAR))
						.build();
		new Matrix4f().store(identityMatrixBuffer);
		identityMatrixBuffer.rewind();

		fullScreenMipmapCount = Util.calculateMipMapCount(Math.max(Config.getInstance().getWidth(), Config.getInstance().getHeight()));
		pixelBufferObject = new PixelBufferObject(1, 1);
		
         storageBuffer = new PersistentMappedBuffer(4*8);//new StorageBuffer(16);
         storageBuffer.putValues(1f,-1f,0f,1f);

        DeferredRenderer.exitOnGLError("grid de.hanno.hpengine.texture creation");
	}
	
	public void init() {
		probeBox = null;
		try {
			probeBox = new OBJLoader().loadTexturedModel(new File(Engine.WORKDIR_NAME + "/assets/models/probebox.obj"));
			Material probeBoxMaterial = MaterialFactory.getInstance().getDefaultMaterial();
			probeBoxMaterial.setDiffuse(new Vector3f(0, 1, 0));
			probeBox.setMaterial(probeBoxMaterial);
            probeBoxEntity = EntityFactory.getInstance().getEntity("ProbeBox", probeBox);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
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

	public OpenGLBuffer getStorageBuffer() {
		return storageBuffer;
	}

	public void setStorageBuffer(StorageBuffer storageBuffer) {
		this.storageBuffer = storageBuffer;
	}

	public void use(boolean clear) {
		gBuffer.use(clear);
	}

	public Entity getProbeBoxEntity() {
		return probeBoxEntity;
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

    public int getLightmapUVMap() {
        return gBuffer.getRenderedTexture(5);
    }
}
