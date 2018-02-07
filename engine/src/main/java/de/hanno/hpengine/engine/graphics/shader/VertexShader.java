package de.hanno.hpengine.engine.graphics.shader;

import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.util.ressources.CodeSource;

import java.io.IOException;

public class VertexShader extends AbstractShader {

    public static VertexShader load(ProgramFactory programFactory, CodeSource sourceCode, Defines defines) throws IOException {
        return Shader.loadShader(programFactory.getGpuContext(), VertexShader.class, sourceCode, defines);
    }

    @Override
    public OpenGLShader getShaderType() {
        return OpenGLShader.VertexShader;
    }
}
