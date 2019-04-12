package de.hanno.hpengine.engine.graphics.shader;

import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.util.ressources.CodeSource;

public class VertexShader extends AbstractShader {

    public static VertexShader load(OpenGlProgramManager programManager, CodeSource sourceCode, Defines defines) {
        return programManager.loadShader(VertexShader.class, sourceCode, defines);
    }

    @Override
    public OpenGLShader getShaderType() {
        return OpenGLShader.VertexShader;
    }
}
