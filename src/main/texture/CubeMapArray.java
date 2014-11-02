package main.texture;

import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.scene.EnvironmentProbeFactory;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

public class CubeMapArray {
	
	private int textureId;

	public CubeMapArray(Renderer renderer, int layerCount) {
		textureId = GL11.glGenTextures();
		bind();

		GL42.glTexStorage3D(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, 9, GL11.GL_RGBA8, EnvironmentProbeFactory.RESOLUTION, EnvironmentProbeFactory.RESOLUTION, layerCount);// 0, GL_RGB, GL_UNSIGNED_BYTE, NULL);

//		for ( int i = 0; i < TEXTURE_NUM; i++ )
//		{
//		    char fileName[32];
//		    sprintf(fileName, "./img/%02d.bmp", i);
//		    unsigned char* imgData;
//		    imgData = loadBMP(fileName);
//		    if (imgData)
//		        printf("imgData %s is successfully loaded\n",fileName);
//		    glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, i, 8, 8, 1, GL_RGB, GL_UNSIGNED_BYTE, imgData);
//		    checkGLErrors("end check");
//		    free(imgData);
//		}
	}
	
	public void copyCubeMapIntoIndex(int cubeMapId, int index) {
		GL43.glCopyImageSubData(cubeMapId, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0,
			textureId, GL40.GL_TEXTURE_CUBE_MAP_ARRAY, 0, 0, 0, index, EnvironmentProbeFactory.RESOLUTION, EnvironmentProbeFactory.RESOLUTION, 0);
	}

	public void bind() {
		GL11.glBindTexture(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, textureId);
	}
	
	public void bind(int target) {
		GL13.glActiveTexture(target);
		GL11.glBindTexture(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, textureId);
	}

}
