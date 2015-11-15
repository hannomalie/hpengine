package shader;

public abstract class AbstractShader implements Shader {
    private int id = -1;
    private ShaderSource shaderSource;

    @Override
    public void setId(int id) {
        this.id = id;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setShaderSource(ShaderSource shaderSource) {
        this.shaderSource = shaderSource;
    }
    @Override
    public ShaderSource getShaderSource() {
        return shaderSource;
    }

    @Override
    public void load() {
        shaderSource.load();
    }

    @Override
    public void unload() {
        shaderSource.unload();
    }

    @Override
    public String getName() {
        return shaderSource.getName();
    }
}
