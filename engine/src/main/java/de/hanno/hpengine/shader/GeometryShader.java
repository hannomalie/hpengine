package de.hanno.hpengine.shader;

import de.hanno.hpengine.util.ressources.CodeSource;

import java.io.IOException;

public class GeometryShader extends AbstractShader {

    public static GeometryShader load(CodeSource sourceCode) throws IOException {
        return Shader.loadShader(GeometryShader.class, sourceCode);
    }

    @Override
    public OpenGLShader getShaderType() {
        return OpenGLShader.GeometryShader;
    }
}
