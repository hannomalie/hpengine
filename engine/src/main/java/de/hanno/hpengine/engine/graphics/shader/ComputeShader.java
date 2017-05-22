package de.hanno.hpengine.engine.graphics.shader;

import de.hanno.hpengine.util.ressources.CodeSource;

import java.io.IOException;

public class ComputeShader extends AbstractShader {

    public static ComputeShader load(CodeSource sourceCode) throws IOException {
        return load(sourceCode, "");
    }

    public static ComputeShader load(CodeSource sourceCode, String localDefinesString) throws IOException {
        return Shader.loadShader(ComputeShader.class, sourceCode, localDefinesString);
    }

    @Override
    public OpenGLShader getShaderType() {
        return OpenGLShader.ComputeShader;
    }
}
