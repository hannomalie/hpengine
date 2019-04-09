package de.hanno.hpengine.engine.graphics.shader;

import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.util.ressources.CodeSource;

public class ComputeShader extends AbstractShader {

    public static ComputeShader load(ProgramManager programManager, CodeSource sourceCode) {
        return load(programManager, sourceCode, new Defines());
    }

    public static ComputeShader load(ProgramManager programManager, CodeSource sourceCode, Defines defines) {
        return programManager.loadShader(ComputeShader.class, sourceCode, defines);
    }

    @Override
    public OpenGLShader getShaderType() {
        return OpenGLShader.ComputeShader;
    }
}
