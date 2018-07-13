package de.hanno.hpengine.engine.model.texture;

import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.EXTTextureSRGB;
import org.lwjgl.opengl.GL11;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_CUBE_MAP;

public class CubeMap implements CubeTexture, Serializable {
	private static final long serialVersionUID = 1L;
	protected final TextureManager textureManager;
	private final String path;
	protected final GlTextureTarget target;

	public List<byte[]> dataList;
	protected final int width;
	protected final int height;
	protected final int minFilter;
	protected final int magFilter;
	protected int srcPixelFormat;
	protected int dstPixelFormat;
	protected long handle;
	protected int textureId;
	protected long lastUsedTimeStamp;

	public CubeMap(TextureManager textureManager, String path, GlTextureTarget target, int width, int height, int minFilter, int magFilter, int srcPixelFormat, int dstPixelFormat) {
		this.textureManager = textureManager;
		this.path = path;
		this.target = target;
		this.width = width;
		this.height = height;
		this.minFilter = minFilter;
		this.magFilter = magFilter;
		this.srcPixelFormat = srcPixelFormat;
		this.dstPixelFormat = dstPixelFormat;
	}
	
	public ByteBuffer buffer(ByteBuffer buffer, byte[] values) {
		buffer.order(ByteOrder.nativeOrder());
		buffer.put(values, 0, values.length);
		buffer.flip();
		return buffer;
	}
	
	public void load(int cubemapFace, ByteBuffer buffer) {
        GL11.glTexImage2D(cubemapFace,
                0, 
				EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT,
				(getWidth()/4),
				(getHeight()/3),
                0, 
                srcPixelFormat, 
                GL11.GL_UNSIGNED_BYTE, 
                buffer);
	}

	public void setData(List<byte[]> byteArrays) {
		dataList = byteArrays;
	}
	
	@Override
	public String toString() {
		return "(Cubemap)" + path;
	}

	public void bind(GpuContext gpuContext, int unit) {
		gpuContext.bindTexture(unit, TEXTURE_CUBE_MAP, textureId);
	}

	@Override
	public int getTextureId() {
		return textureId;
	}

	@NotNull
	@Override
	public GlTextureTarget getTarget() {
		return target;
	}

	@Override
	public void setTarget(@NotNull GlTextureTarget glTextureTarget) {
		// TODO: Remove this
		throw new IllegalStateException("");
	}

	@Override
	public long getHandle() {
		return handle;
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public long getLastUsedTimeStamp() {
		return lastUsedTimeStamp;
	}

	@Override
	public int getMinFilter() {
		return minFilter;
	}

	@Override
	public int getMagFilter() {
		return magFilter;
	}

	@Override
	public void unload() {}

	void bind() {
		textureManager.getGpuContext().bindTexture(target, textureId);
	}

	public void bind(int textureUnitIndex) {
		textureManager.getGpuContext().bindTexture(textureUnitIndex, target, textureId);
	}

	public void setHandle(long handle) {
		this.handle = handle;
	}

	@Override
	public void setUsedNow() {
//		TODO Implement me
	}

	@Override
	public List<? extends byte[]> getData() {
		return dataList;
	}
}
