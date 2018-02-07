package de.hanno.hpengine.engine.graphics.shader;

import de.hanno.hpengine.engine.graphics.renderer.GpuContext;
import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.util.ressources.CodeSource;

import java.io.IOException;

public class ComputeShader extends AbstractShader {

    public static ComputeShader load(GpuContext gpuContext, CodeSource sourceCode) throws IOException {
        return load(gpuContext, sourceCode, new Defines());
    }

    public static ComputeShader load(GpuContext gpuContext, CodeSource sourceCode, Defines defines) throws IOException {
        return Shader.loadShader(gpuContext, ComputeShader.class, sourceCode, defines);
    }

    @Override
    public OpenGLShader getShaderType() {
        return OpenGLShader.ComputeShader;
    }
}
