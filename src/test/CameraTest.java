package test;

import junit.framework.Assert;
import main.Camera;
import main.ForwardRenderer;
import main.Spotlight;

import org.junit.Test;
import org.lwjgl.util.vector.Vector3f;

public class CameraTest {
	@Test
	public void rotation() {
		Camera camera = new Camera(new ForwardRenderer(new Spotlight(true)));
		Assert.assertEquals(new Vector3f(0,1,0), camera.getUp());
		
		camera.rotate(new Vector3f(0,1,0), 90f, true);

		float epsilon = 0.01f;
		Assert.assertEquals(0, camera.getUp().x, epsilon);
		Assert.assertEquals(1, camera.getUp().y, epsilon);
		Assert.assertEquals(0, camera.getUp().z, epsilon);

		Assert.assertEquals(0, camera.getRight().x, epsilon);
		Assert.assertEquals(0, camera.getRight().y, epsilon);
		Assert.assertEquals(-1, camera.getRight().z, epsilon);
		
		Assert.assertEquals(-1, -camera.getBack().x, epsilon); // negate z, opengl stuff
		Assert.assertEquals(0, camera.getBack().y, epsilon);
		Assert.assertEquals(0, camera.getBack().z, epsilon);
	}
}
