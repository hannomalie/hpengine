package de.hanno.hpengine.engine.graphics.shader;

import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.util.ressources.CodeSource;

import java.io.IOException;

public class ComputeShader extends AbstractShader {

    public static ComputeShader load(CodeSource sourceCode) throws IOException {
        return load(sourceCode, new Defines());
    }

    public static ComputeShader load(CodeSource sourceCode, Defines defines) throws IOException {
        return Shader.loadShader(ComputeShader.class, sourceCode, defines);
    }

    @Override
    public OpenGLShader getShaderType() {
        return OpenGLShader.ComputeShader;
    }
}
