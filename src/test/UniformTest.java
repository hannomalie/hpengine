package test;

import java.nio.FloatBuffer;

import main.World;
import main.model.Entity;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.shader.Program;
import main.shader.StorageBuffer;
import main.shader.Uniform;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.lwjgl.BufferUtils;

public class UniformTest extends TestWithRenderer {

	@Test
	public void uniforms() {
		Program program = renderer.getProgramFactory().getProgram("second_pass_point_vertex.glsl", "second_pass_point_fragment.glsl", Entity.POSITIONCHANNEL, false);
		
		Uniform uniform = new Uniform(program, "test");
		Assert.assertTrue(program.getUniform("test").equals(uniform));
		
		program.setUniform("lightPosition", 1,1,1);
	}
}
