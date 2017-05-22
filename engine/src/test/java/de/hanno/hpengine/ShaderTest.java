package de.hanno.hpengine;

import de.hanno.hpengine.util.ressources.CodeSource;
import org.junit.Assert;
import org.junit.Test;
import de.hanno.hpengine.engine.graphics.shader.*;

import java.io.IOException;

import static de.hanno.hpengine.engine.graphics.shader.Shader.*;

public class ShaderTest extends TestWithRenderer {

    @Test
    public void loadsCorrectShaderTypes() throws IOException {

        CodeSource shaderSource = ShaderSourceFactory.getShaderSource("void main() {}");

        {
            VertexShader vertexShader = VertexShader.load(shaderSource);
            Assert.assertTrue("", vertexShader.getShaderType().equals(OpenGLShader.VertexShader));
            Assert.assertTrue(vertexShader.getId() > 0);
        }
        {
            GeometryShader geometryShader = GeometryShader.load(shaderSource);
            Assert.assertTrue("", geometryShader.getShaderType().equals(OpenGLShader.GeometryShader));
            Assert.assertTrue(geometryShader.getId() > 0);
        }
        {
            FragmentShader fragmentShader = FragmentShader.load(shaderSource);
            Assert.assertTrue("", fragmentShader.getShaderType().equals(OpenGLShader.FragmentShader));
            Assert.assertTrue(fragmentShader.getId() > 0);
        }
    }
}
