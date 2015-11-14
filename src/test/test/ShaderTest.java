package test;

import org.junit.Assert;
import org.junit.Test;
import renderer.material.Material.MAP;
import shader.*;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static shader.Shader.*;

public class ShaderTest extends TestWithRenderer {

	@Test
	public void shaderDefines() {

		String expected = "#define use_diffuseMap\n#define use_normalMap\n";

		String defines = ShaderDefine.getDefineString(EnumSet.of(ShaderDefine.DIFFUSE, ShaderDefine.NORMAL));
		Assert.assertEquals(expected, defines);

		Set<MAP> mapSet = new HashSet<>();
		mapSet.add(MAP.DIFFUSE);
		mapSet.add(MAP.NORMAL);
		defines = ShaderDefine.getDefinesString(mapSet);

		Assert.assertTrue(expected.contains(ShaderDefine.DIFFUSE.getDefineText() + "\n"));
		Assert.assertTrue(expected.contains(ShaderDefine.NORMAL.getDefineText() + "\n"));
	}

	@Test
	public void localShaderDefines() {
//		Program program = renderer.getProgramFactory().getProgram("");
		String defineStringForBoolean = Program.getDefineTextForObject(new Map.Entry<String, Object>() {
			@Override
			public String getKey() {
				return "variableName";
			}

			@Override
			public Object getValue() {
				return true;
			}

			@Override
			public Object setValue(Object value) {
				return true;
			}
		});

		Assert.assertEquals("const bool variableName = true;\n", defineStringForBoolean);
	}

    @Test
    public void loadsCorrectShaderTypes() throws IOException {

        ShaderSource shaderSource = new ShaderSource("void main() {}");

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
