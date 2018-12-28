package de.hanno.hpengine.engine.model.texture;

import de.hanno.hpengine.engine.backend.Backend;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget;
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig;
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig.MinFilter;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;

@SuppressWarnings("serial")
public class DynamicCubeMap extends CubeMap {

    private Backend engine;

    public DynamicCubeMap(Backend engine, int resolution, int internalFormat, int type, MinFilter minFilter, int format, FloatBuffer[] values) {
        super(engine.getTextureManager(), "", new TextureDimension(resolution, resolution), new TextureFilterConfig(minFilter), GL11.GL_RGBA, engine.getGpuContext().genTextures(), null); // TODO: pass formats
        if(values.length != 6) { throw new IllegalArgumentException("Pass six float buffers with values!"); }
        this.engine = engine;

        engine.getGpuContext().bindTexture(0, GlTextureTarget.TEXTURE_CUBE_MAP, getTextureId());

        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL14.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, getTextureFilterConfig().getMagFilter().getGlValue());
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, getTextureFilterConfig().getMinFilter().getGlValue());
        GL30.glGenerateMipmap(GL13.GL_TEXTURE_CUBE_MAP);

        GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X, 0, internalFormat, resolution, resolution, 0, format, type, values[0]);
        GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+1, 0, internalFormat, resolution, resolution, 0, format, type, values[1]);
        GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+2, 0, internalFormat, resolution, resolution, 0, format, type, values[2]);
        GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+3, 0, internalFormat, resolution, resolution, 0, format, type, values[3]);
        GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+4, 0, internalFormat, resolution, resolution, 0, format, type, values[4]);
        GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+5, 0, internalFormat, resolution, resolution, 0, format, type, values[5]);

        getTextureManager().createTextureHandleAndMakeResident(this);
    }
}
