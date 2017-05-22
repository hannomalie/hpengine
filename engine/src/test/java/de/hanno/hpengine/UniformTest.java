package de.hanno.hpengine;

import de.hanno.hpengine.engine.graphics.shader.Shader;
import org.junit.Assert;
import org.junit.Test;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.shader.ProgramFactory;
import de.hanno.hpengine.engine.graphics.shader.Uniform;

import java.io.File;

public class UniformTest extends TestWithRenderer {

	@Test
	public void uniforms() {
		Program program = ProgramFactory.getInstance().getProgram(false, Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "second_pass_point_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "second_pass_point_fragment.glsl")));
		
		Uniform uniform = new Uniform(program, "de/hanno/hpengine/test");
		Assert.assertTrue(program.getUniform("de/hanno/hpengine/test").equals(uniform));
		
		program.setUniform("lightPosition", 1,1,1);
	}
}
