package de.hanno.hpengine.engine.model.texture;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;

@SuppressWarnings("serial")
public class DynamicCubeMap extends CubeMap {

    private Engine engine;

    public DynamicCubeMap(Engine engine, int resolution, int internalFormat, int type, int minFilter, int format, FloatBuffer[] values) {
        super(engine.getTextureManager(), "", GlTextureTarget.TEXTURE_CUBE_MAP, resolution, resolution, minFilter, GL11.GL_LINEAR, GL11.GL_RGBA, GL11.GL_RGBA ); // TODO: pass formats
        if(values.length != 6) { throw new IllegalArgumentException("Pass six float buffers with values!"); }
        this.engine = engine;
		this.textureId = createTextureID();

        engine.getGpuContext().bindTexture(0, GlTextureTarget.TEXTURE_CUBE_MAP, textureId);

        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL14.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
        GL30.glGenerateMipmap(GL13.GL_TEXTURE_CUBE_MAP);

        GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X, 0, internalFormat, resolution, resolution, 0, format, type, values[0]);
        GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+1, 0, internalFormat, resolution, resolution, 0, format, type, values[1]);
        GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+2, 0, internalFormat, resolution, resolution, 0, format, type, values[2]);
        GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+3, 0, internalFormat, resolution, resolution, 0, format, type, values[3]);
        GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+4, 0, internalFormat, resolution, resolution, 0, format, type, values[4]);
        GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+5, 0, internalFormat, resolution, resolution, 0, format, type, values[5]);

        genHandle(engine.getTextureManager());
    }

    protected void genHandle(TextureManager textureManager) {
        if (handle <= 0) {
            handle = textureManager.getGpuContext().calculate(() -> {
                bind(15);
                long theHandle = ARBBindlessTexture.glGetTextureHandleARB(textureId);
                ARBBindlessTexture.glMakeTextureHandleResidentARB(theHandle);
                unbind(15);
                return theHandle;
            });
        }
    }

    void unbind(int unit) {
        textureManager.getGpuContext().bindTexture(unit, target, 0);
    }
    private int createTextureID() 
    {
       return GL11.glGenTextures();
    }

    public void bind() {
        engine.getGpuContext().bindTexture(GlTextureTarget.TEXTURE_CUBE_MAP, textureId);
    }
}
