package test;

import junit.framework.Assert;
import main.Camera;
import main.ForwardRenderer;
import main.Frustum;
import main.Renderer;
import main.Spotlight;
import main.util.Util;

import org.junit.Before;
import org.junit.Test;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

public class CameraTest {
	
	Renderer renderer;
	
	@Before
	public void init() {
		renderer = new ForwardRenderer(new Spotlight(true));
	}
	
	@Test
	public void rotation() {
		Camera camera = new Camera(renderer);
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
	
	@Test
	public void inFrustum() {
		Matrix4f projectionMatrix = Util.createPerpective(60, 16/9, 0.1f, 100f);
		Camera camera = new Camera(renderer, projectionMatrix);
		Assert.assertEquals(new Vector3f(0,0,1), camera.getBack());
		
		Frustum frustum = camera.getFrustum();

//		Assert.assertEquals(0.1f, frustum.values[Frustum.FRONT][Frustum.D], 0.0001f);
//		Assert.assertEquals(100f, frustum.values[Frustum.BACK][Frustum.D], 0.0001f);
		Assert.assertFalse(frustum.pointInFrustum(0, 0, 1));
		Assert.assertTrue(frustum.pointInFrustum(0, 0, -1));
		
		camera.move(new Vector3f(0,0,5));
		Assert.assertTrue(frustum.pointInFrustum(0, 0, 1));
		
		camera.setPosition(new Vector3f());
		camera.rotate(new Vector3f(0,1,0), 180, true);
		Assert.assertTrue(frustum.pointInFrustum(0, 0, 1));
		
	}
}
