package test;

import main.World;
import main.model.Entity;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.shader.Program;
import main.shader.Uniform;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class UniformTest {

	Renderer renderer;
	Program program;
	
	@Before
	public void init() {
		renderer = new DeferredRenderer(new World());
		program = renderer.getProgramFactory().getProgram("second_pass_point_vertex.glsl", "second_pass_point_fragment.glsl", Entity.POSITIONCHANNEL, false);
		
	}
	@Test
	public void uniforms() {
		
		Uniform uniform = new Uniform(program, "test");
		Assert.assertTrue(program.getUniform("test").equals(uniform));
		
		program.setUniform("lightPosition", 1,1,1);
	}

}
