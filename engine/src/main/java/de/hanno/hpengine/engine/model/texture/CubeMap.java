package de.hanno.hpengine.engine.model.texture;

import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.opengl.EXTTextureSRGB;
import org.lwjgl.opengl.GL11;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_CUBE_MAP;

public class CubeMap extends Texture implements Serializable {
	private static final long serialVersionUID = 1L;
	
	public List<byte[]> dataList;

	protected CubeMap(TextureManager textureManager) {
		super(textureManager);
	}
	
	public CubeMap(TextureManager textureManager, String path, GlTextureTarget target) {
		super(textureManager, path, target, false);
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
		return "(Cubemap)" + getPath();
	}

	public void bind(GpuContext gpuContext, int unit) {
		gpuContext.bindTexture(unit, TEXTURE_CUBE_MAP, textureID);
	}
}
