package de.hanno.hpengine;

import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.shader.Shader;
import de.hanno.hpengine.engine.graphics.shader.Uniform;
import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

import static de.hanno.hpengine.engine.graphics.shader.ShaderKt.getShaderSource;

public class UniformTest extends TestWithEngine {

	@Test
	public void uniforms() {
        Program program = engine.getProgramManager().getProgram(getShaderSource(new File(Shader.directory + "second_pass_point_vertex.glsl")), getShaderSource(new File(Shader.directory + "second_pass_point_fragment.glsl")), new Defines());
		
		Uniform uniform = new Uniform(program, "de/hanno/hpengine/test");
		Assert.assertTrue(program.getUniform("de/hanno/hpengine/test").equals(uniform));
		
		program.setUniform("lightPosition", 1,1,1);
	}
}
