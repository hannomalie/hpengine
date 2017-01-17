package de.hanno.hpengine.texture;

import org.lwjgl.opengl.*;
import de.hanno.hpengine.renderer.OpenGLContext;
import de.hanno.hpengine.renderer.constants.GlTextureTarget;

public class CubeMapArray {

    private final int resolution;
    private final int cubemapCount;
    private int textureId;
	private int mipMapCount = 1;
	private int internalFormat;
	
	public CubeMapArray(int textureCount, int resolution) {
		this(textureCount, GL11.GL_LINEAR_MIPMAP_LINEAR, resolution);
	}
	
	public CubeMapArray(int textureCount, int magTextureFilter, int resolution) {
		this(textureCount, magTextureFilter, GL30.GL_RGBA16F, resolution);
	}
	/**
	 * @param resolution
     * @param textureCount the actual number of cubemap textures you want to allocate
	 */
	public CubeMapArray(int textureCount, int magTextureFilter, int internalFormat, int resolution) {
        this.resolution = resolution;
		OpenGLContext.getInstance().execute(() -> {
			textureId = GL11.glGenTextures();
			bind();

            if(Texture.filterRequiresMipmaps(magTextureFilter)) {
//                mipMapCount = EnvironmentProbeFactory.CUBEMAPMIPMAPCOUNT;
                mipMapCount = de.hanno.hpengine.util.Util.calculateMipMapCount(resolution);
            }
			this.internalFormat = internalFormat;
			GL42.glTexStorage3D(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, mipMapCount, internalFormat, resolution, resolution, textureCount*6);

			GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, magTextureFilter);
			GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);

			if(Texture.filterRequiresMipmaps(magTextureFilter)) {
				GL30.glGenerateMipmap(GL40.GL_TEXTURE_CUBE_MAP_ARRAY);
			}
		});
        this.cubemapCount = textureCount;
	}
	
	/**
	 * @param cubeMapId the id of the source cubemap you want to copy into this array
	 * @param index the actual index you want your cubemap to be placed in
	 */
	public void copyCubeMapIntoIndex(int cubeMapId, int index) {
		for(int i = 0; i < mipMapCount+1; i++) {
			GL43.glCopyImageSubData(cubeMapId, GL13.GL_TEXTURE_CUBE_MAP, i, 0, 0, 0,
				textureId, GL40.GL_TEXTURE_CUBE_MAP_ARRAY, i, 0, 0, 6*index, resolution, resolution, 6);
		}
	}

	public void bind() {
		OpenGLContext.getInstance().bindTexture(GlTextureTarget.TEXTURE_CUBE_MAP_ARRAY, textureId);
	}

	public void bind(int unit) {
		OpenGLContext.getInstance().bindTexture(unit, GlTextureTarget.TEXTURE_CUBE_MAP_ARRAY, textureId);
	}
	public void bind(int layer, int unit) {
		bind(layer, unit, 0);
	}
	public void bind(int layer, int unit, int level) {
		OpenGLContext.getInstance().bindImageTexture(unit, textureId, level, false, layer, GL15.GL_READ_WRITE, internalFormat);
	}

	public int getTextureID() {
		return textureId;
	}

	public int getInternalFormat() {
		return internalFormat;
	}

    public int getCubemapCount() {
        return cubemapCount;
    }
}
