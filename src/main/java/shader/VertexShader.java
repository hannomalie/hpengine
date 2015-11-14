package shader;

import java.io.IOException;

public class VertexShader extends AbstractShader {

    public static VertexShader load(ShaderSource sourceCode) throws IOException {
        return Shader.loadShader(VertexShader.class, sourceCode);
    }

    @Override
    public OpenGLShader getShaderType() {
        return OpenGLShader.VertexShader;
    }
}
