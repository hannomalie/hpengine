package test;

import component.ModelComponent;
import org.junit.Assert;
import org.junit.Test;
import shader.Program;
import shader.Uniform;

public class UniformTest extends TestWithRenderer {

	@Test
	public void uniforms() {
		Program program = renderer.getProgramFactory().getProgram("second_pass_point_vertex.glsl", "second_pass_point_fragment.glsl", ModelComponent.POSITIONCHANNEL, false);
		
		Uniform uniform = new Uniform(program, "test");
		Assert.assertTrue(program.getUniform("test").equals(uniform));
		
		program.setUniform("lightPosition", 1,1,1);
	}
}
