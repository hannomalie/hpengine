package de.hanno.hpengine.engine.model.texture;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;

@SuppressWarnings("serial")
public class DynamicCubeMap extends CubeMap {

    private Engine engine;

    public DynamicCubeMap(Engine engine, int width, int height, int internalFormat, int type) {
        super(engine.getTextureManager());
        this.engine = engine;
		this.width = width;
		this.height = height;
		this.textureID = createTextureID();
		FloatBuffer dummy = BufferUtils.createFloatBuffer(width*height * 4 * 6);

        engine.getGpuContext().bindTexture(0, GlTextureTarget.TEXTURE_CUBE_MAP, textureID);

        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL14.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_NEAREST);
        GL30.glGenerateMipmap(GL13.GL_TEXTURE_CUBE_MAP);

        GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+0, 0, internalFormat, width, height, 0, GL11.GL_RGBA, type, dummy);
        GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+1, 0, internalFormat, width, height, 0, GL11.GL_RGBA, type, dummy);
        GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+2, 0, internalFormat, width, height, 0, GL11.GL_RGBA, type, dummy);
        GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+3, 0, internalFormat, width, height, 0, GL11.GL_RGBA, type, dummy);
        GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+4, 0, internalFormat, width, height, 0, GL11.GL_RGBA, type, dummy);
        GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+5, 0, internalFormat, width, height, 0, GL11.GL_RGBA, type, dummy);

        genHandle(engine.getTextureManager());
    }
	
    private int createTextureID() 
    {
       return GL11.glGenTextures();
    }

    public void bind() {
        engine.getGpuContext().bindTexture(GlTextureTarget.TEXTURE_CUBE_MAP, textureID);
    }
}
