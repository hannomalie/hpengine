package shader;

import java.io.IOException;

public class GeometryShader extends AbstractShader {

    public static GeometryShader load(ShaderSource sourceCode) throws IOException {
        return Shader.loadShader(GeometryShader.class, sourceCode);
    }

    @Override
    public OpenGLShader getShaderType() {
        return OpenGLShader.GeometryShader;
    }
}
