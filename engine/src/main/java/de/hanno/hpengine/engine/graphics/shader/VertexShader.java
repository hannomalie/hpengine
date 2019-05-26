package de.hanno.hpengine.engine.graphics.shader;

public class VertexShader extends AbstractShader {

    @Override
    public OpenGLShader getShaderType() {
        return OpenGLShader.VertexShader;
    }
}
