package shader;

import java.io.IOException;

public class ComputeShader extends AbstractShader {

    public static ComputeShader load(ShaderSource sourceCode) throws IOException {
        return Shader.loadShader(ComputeShader.class, sourceCode);
    }

    @Override
    public OpenGLShader getShaderType() {
        return OpenGLShader.ComputeShader;
    }
}
