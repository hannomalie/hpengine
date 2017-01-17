package de.hanno.hpengine.shader;

import java.io.IOException;

public class ComputeShader extends AbstractShader {

    public static ComputeShader load(ShaderSource sourceCode) throws IOException {
        return load(sourceCode, "");
    }

    public static ComputeShader load(ShaderSource sourceCode, String localDefinesString) throws IOException {
        return Shader.loadShader(ComputeShader.class, sourceCode, localDefinesString);
    }

    @Override
    public OpenGLShader getShaderType() {
        return OpenGLShader.ComputeShader;
    }
}
