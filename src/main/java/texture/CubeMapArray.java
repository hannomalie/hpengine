package texture;

import renderer.Renderer;
import scene.EnvironmentProbeFactory;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

public class CubeMapArray {
	
	private int textureId;
	private int mipMapCount;
	private int internalFormat;
	
	transient Renderer renderer;

	public CubeMapArray(Renderer renderer, int textureCount) {
		this(renderer, textureCount, GL11.GL_LINEAR_MIPMAP_LINEAR);
	}
	
	public CubeMapArray(Renderer renderer, int textureCount, int magTextureFilter) {
		this(renderer, textureCount, magTextureFilter, GL30.GL_RGBA16F);
	}
	/**
	 * @param renderer
	 * @param textureCount the actual number of cubemap textures you want to allocate
	 */
	public CubeMapArray(Renderer renderer, int textureCount, int magTextureFilter, int internalFormat) {
		textureId = GL11.glGenTextures();
		bind();

		this.renderer = renderer;
		mipMapCount = EnvironmentProbeFactory.CUBEMAPMIPMAPCOUNT;
		this.internalFormat = internalFormat;
		GL42.glTexStorage3D(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, mipMapCount, internalFormat, EnvironmentProbeFactory.RESOLUTION, EnvironmentProbeFactory.RESOLUTION, textureCount*6);

		GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, magTextureFilter);
		GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		
		if(Texture.filterRequiresMipmaps(magTextureFilter)) {
			GL30.glGenerateMipmap(GL40.GL_TEXTURE_CUBE_MAP_ARRAY);
		}
	}
	
	/**
	 * @param cubeMapId the id of the source cubemap you want to copy into this array
	 * @param index the actual index you want your cubemap to be placed in
	 */
	public void copyCubeMapIntoIndex(int cubeMapId, int index) {
		for(int i = 0; i < mipMapCount+1; i++) {
			GL43.glCopyImageSubData(cubeMapId, GL13.GL_TEXTURE_CUBE_MAP, i, 0, 0, 0,
				textureId, GL40.GL_TEXTURE_CUBE_MAP_ARRAY, i, 0, 0, 6*index, EnvironmentProbeFactory.RESOLUTION, EnvironmentProbeFactory.RESOLUTION, 6);
		}
	}

	public void bind() {
		GL11.glBindTexture(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, textureId);
	}

	public void bind(int unit) {
		GL13.glActiveTexture(unit);
		GL11.glBindTexture(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, textureId);
	}
	public void bind(int layer, int unit) {
		bind(layer, unit, 0);
	}
	public void bind(int layer, int unit, int level) {
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
		GL42.glBindImageTexture(unit, textureId, level, false, layer, GL15.GL_READ_WRITE, internalFormat);
	}

	public int getTextureID() {
		return textureId;
	}

	public int getInternalFormat() {
		return internalFormat;
	}

}
