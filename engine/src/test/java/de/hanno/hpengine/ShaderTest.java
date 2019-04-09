package de.hanno.hpengine;

import de.hanno.hpengine.engine.graphics.shader.FragmentShader;
import de.hanno.hpengine.engine.graphics.shader.GeometryShader;
import de.hanno.hpengine.engine.graphics.shader.ProgramManager;
import de.hanno.hpengine.engine.graphics.shader.VertexShader;
import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.util.ressources.CodeSource;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

import static de.hanno.hpengine.engine.graphics.shader.Shader.OpenGLShader;
import static de.hanno.hpengine.engine.graphics.shader.ShaderKt.getShaderSource;

public class ShaderTest extends TestWithEngine {

    @Test
    public void loadsCorrectShaderTypes() {

        CodeSource shaderSource = getShaderSource("void main() {}");

        ProgramManager programManager = engine.getProgramManager();
        {
            VertexShader vertexShader = VertexShader.load(programManager, shaderSource, new Defines());
            Assert.assertTrue("", vertexShader.getShaderType().equals(OpenGLShader.VertexShader));
            Assert.assertTrue(vertexShader.getId() > 0);
        }
        {
            GeometryShader geometryShader = GeometryShader.load(programManager, shaderSource);
            Assert.assertTrue("", geometryShader.getShaderType().equals(OpenGLShader.GeometryShader));
            Assert.assertTrue(geometryShader.getId() > 0);
        }
        {
            FragmentShader fragmentShader = FragmentShader.load(programManager, shaderSource);
            Assert.assertTrue("", fragmentShader.getShaderType().equals(OpenGLShader.FragmentShader));
            Assert.assertTrue(fragmentShader.getId() > 0);
        }
    }
}
