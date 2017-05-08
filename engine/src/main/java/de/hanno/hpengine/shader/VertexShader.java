package de.hanno.hpengine.shader;

import de.hanno.hpengine.util.ressources.CodeSource;

import java.io.IOException;

public class VertexShader extends AbstractShader {

    public static VertexShader load(CodeSource sourceCode) throws IOException {
        return Shader.loadShader(VertexShader.class, sourceCode);
    }

    @Override
    public OpenGLShader getShaderType() {
        return OpenGLShader.VertexShader;
    }
}
