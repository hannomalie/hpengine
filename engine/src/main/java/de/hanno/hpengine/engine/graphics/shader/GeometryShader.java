package de.hanno.hpengine.engine.graphics.shader;

import de.hanno.hpengine.util.ressources.CodeSource;

import java.io.IOException;

public class GeometryShader extends AbstractShader {

    public static GeometryShader load(ProgramManager programManager, CodeSource sourceCode) {
        return programManager.loadShader(GeometryShader.class, sourceCode);
    }

    @Override
    public OpenGLShader getShaderType() {
        return OpenGLShader.GeometryShader;
    }
}
