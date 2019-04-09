package de.hanno.hpengine.engine.graphics.shader;

import de.hanno.hpengine.util.ressources.CodeSource;

public class FragmentShader extends AbstractShader {

    public static FragmentShader load(ProgramManager programManager, CodeSource sourceCode) {
        return programManager.loadShader(FragmentShader.class, sourceCode);
    }

    @Override
    public OpenGLShader getShaderType() {
        return OpenGLShader.FragmentShader;
    }
}
