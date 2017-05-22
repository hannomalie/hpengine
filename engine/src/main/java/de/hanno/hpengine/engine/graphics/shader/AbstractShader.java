package de.hanno.hpengine.engine.graphics.shader;

import de.hanno.hpengine.util.ressources.CodeSource;

public abstract class AbstractShader implements Shader {
    private int id = -1;
    private CodeSource shaderSource;

    @Override
    public void setId(int id) {
        this.id = id;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setShaderSource(CodeSource shaderSource) {
        this.shaderSource = shaderSource;
    }
    @Override
    public CodeSource getShaderSource() {
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
