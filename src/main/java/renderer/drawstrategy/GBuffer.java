package renderer.drawstrategy;

import config.Config;
import engine.AppContext;
import engine.model.Entity;
import engine.model.EntityFactory;
import engine.model.Model;
import engine.model.OBJLoader;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import renderer.DeferredRenderer;
import renderer.PixelBufferObject;
import renderer.Renderer;
import renderer.material.Material;
import renderer.material.MaterialFactory;
import renderer.rendertarget.ColorAttachmentDefinition;
import renderer.rendertarget.RenderTarget;
import renderer.rendertarget.RenderTargetBuilder;
import shader.StorageBuffer;
import util.Util;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

public class GBuffer {

	public static volatile int IMPORTANCE_SAMPLE_COUNT = 8;
	public static float SECONDPASSSCALE = 1f;
	public static volatile boolean USE_COMPUTESHADER_FOR_REFLECTIONS = false;
	public static volatile boolean RENDER_PROBES_WITH_FIRST_BOUNCE = true;
	public static volatile boolean RENDER_PROBES_WITH_SECOND_BOUNCE = true;
    public final int grid;

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
	
	private StorageBuffer storageBuffer;

	private final int exposureIndex = 0;
	private AppContext appContext;

	public GBuffer(AppContext appContext, Renderer renderer) {
		this.appContext = appContext;

        gBuffer = new RenderTargetBuilder().setWidth(Config.WIDTH).setHeight(Config.HEIGHT)
						.add(5, new ColorAttachmentDefinition().setInternalFormat(GL30.GL_RGBA16F))
						.build();
		reflectionBuffer = new RenderTargetBuilder().setWidth(Config.WIDTH).setHeight(Config.HEIGHT)
						.add(2, new ColorAttachmentDefinition()
								.setInternalFormat(GL30.GL_RGBA16F)
								.setTextureFilter(GL11.GL_LINEAR))
						.setClearRGBA(0, 0, 0, 0)
						.build();
		laBuffer = new RenderTargetBuilder().setWidth((int) (Config.WIDTH * SECONDPASSSCALE))
						.setHeight((int) (Config.HEIGHT * SECONDPASSSCALE))
						.add(2, new ColorAttachmentDefinition().setInternalFormat(GL30.GL_RGBA16F))
						.build();
		finalBuffer = new RenderTargetBuilder().setWidth(Config.WIDTH).setHeight(Config.HEIGHT)
						.add(new ColorAttachmentDefinition()
								.setInternalFormat(GL11.GL_RGBA8))
						.build();
		halfScreenBuffer = new RenderTargetBuilder().setWidth(Config.WIDTH / 2).setHeight(Config.HEIGHT / 2)
						.add(new ColorAttachmentDefinition()
								.setInternalFormat(GL30.GL_RGBA16F))
						.build();
		new Matrix4f().store(identityMatrixBuffer);
		identityMatrixBuffer.rewind();

		fullScreenMipmapCount = Util.calculateMipMapCount(Math.max(Config.WIDTH, Config.HEIGHT));
		pixelBufferObject = new PixelBufferObject(1, 1);
		
         storageBuffer = new StorageBuffer(16);
         storageBuffer.putValues(1f,-1f,0f,1f);

        grid = GL11.glGenTextures();
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, grid);
//        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
//        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_MAX_LEVEL, 8);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        int gridSize = 256;
        GL42.glTexStorage3D(GL12.GL_TEXTURE_3D, 8, GL30.GL_RGBA16F, gridSize, gridSize, gridSize);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, grid);
        GL30.glGenerateMipmap(GL12.GL_TEXTURE_3D);

//        long handle =  ARBBindlessTexture.glGetTextureHandleARB(grid);
//        ARBBindlessTexture.glMakeTextureHandleResidentARB(handle);
        DeferredRenderer.exitOnGLError("grid texture creation");
	}
	
	public void init() {
		probeBox = null;
		try {
			probeBox = new OBJLoader().loadTexturedModel(new File(AppContext.WORKDIR_NAME + "/assets/models/probebox.obj")).get(0);
			Material probeBoxMaterial = MaterialFactory.getInstance().getDefaultMaterial();
			probeBoxMaterial.setDiffuse(new Vector3f(0, 1, 0));
			probeBox.setMaterial(probeBoxMaterial);
            probeBoxEntity = EntityFactory.getInstance().getEntity(probeBox, probeBoxMaterial);
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

	public StorageBuffer getStorageBuffer() {
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
}
