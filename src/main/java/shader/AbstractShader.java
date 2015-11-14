package shader;

public abstract class AbstractShader implements Shader {
    private int id = -1;

    @Override
    public void setId(int id) {
        this.id = id;
    }

    @Override
    public int getId() {
        return id;
    }
}
