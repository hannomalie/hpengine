package de.hanno.hpengine.test;

import org.junit.Assert;
import org.junit.Test;
import de.hanno.hpengine.shader.Program;
import de.hanno.hpengine.shader.ProgramFactory;
import de.hanno.hpengine.shader.Uniform;

public class UniformTest extends TestWithRenderer {

	@Test
	public void uniforms() {
		Program program = ProgramFactory.getInstance().getProgram("second_pass_point_vertex.glsl", "second_pass_point_fragment.glsl", false);
		
		Uniform uniform = new Uniform(program, "de/hanno/hpengine/test");
		Assert.assertTrue(program.getUniform("de/hanno/hpengine/test").equals(uniform));
		
		program.setUniform("lightPosition", 1,1,1);
	}
}
