package shader;

public class FragmentShader extends AbstractShader {

    public static FragmentShader load(ShaderSource sourceCode) {
        return Shader.loadShader(FragmentShader.class, sourceCode);
    }

    @Override
    public OpenGLShader getShaderType() {
        return OpenGLShader.FragmentShader;
    }
}
