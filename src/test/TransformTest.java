package test;

import junit.framework.Assert;
import main.Transform;

import org.junit.Test;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class TransformTest {
	@Test
	public void initTest() {
		Transform transform = new Transform();
		Assert.assertEquals(new Vector3f(), transform.getPosition());
		Assert.assertTrue(quaternionEqualsHelper(new Quaternion(), transform.getOrientation()));
		Assert.assertEquals(new Vector3f(1,1,1), transform.getScale());
	}

	@Test
	public void directionsTest() {
		Transform transform = new Transform();
		assertEpsilonEqual(Transform.WORLD_VIEW, transform.getViewDirection(), 0.1f);
		assertEpsilonEqual(Transform.WORLD_RIGHT, transform.getRightDirection(), 0.1f);
		assertEpsilonEqual(Transform.WORLD_UP, transform.getUpDirection(), 0.1f);
	}
	
	@Test
	public void translationTest() {
		Transform transform = new Transform();
		transform.move(new Vector3f(5,5,5));
		assertEpsilonEqual(new Vector3f(5,5,5), transform.getPosition(), 0.1f);

		transform = new Transform();
		transform.rotate(Transform.WORLD_UP, 90);
		transform.move(new Vector3f(5,5,5));
		assertEpsilonEqual(new Vector3f(-5,5,5), transform.getPosition(), 0.1f);
		
		transform.moveInWorld(new Vector3f(5,-5,-5));
		assertEpsilonEqual(new Vector3f(), transform.getPosition(), 0.1f);
	}
	
	@Test
	public void rotatationTest() {
		Transform transform = new Transform();
		transform.rotateWorld(new Vector3f(0,1,0), 90);
		assertEpsilonEqual(new Vector3f(1,0,0), transform.getViewDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,1,0), transform.getUpDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,0,1), transform.getRightDirection(), 0.1f);

		transform.rotateWorld(new Vector3f(0,1,0), -90);
		assertEpsilonEqual(Transform.WORLD_VIEW, transform.getViewDirection(), 0.1f);
		assertEpsilonEqual(Transform.WORLD_RIGHT, transform.getRightDirection(), 0.1f);
		assertEpsilonEqual(Transform.WORLD_UP, transform.getUpDirection(), 0.1f);

		transform = new Transform();
		transform.rotateWorld(new Vector3f(1,0,0), 90);
		assertEpsilonEqual(new Vector3f(0,-1,0), transform.getViewDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(1,0,0), transform.getRightDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,0,-1), transform.getUpDirection(), 0.1f);

		// WORLD_VIEW TRANSFORMS WITH -Z!
		transform = new Transform();
		transform.rotateWorld(new Vector3f(0,0,-1), 90);
		assertEpsilonEqual(new Vector3f(0,0,-1), transform.getViewDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(-1,0,0), transform.getUpDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,1,0), transform.getRightDirection(), 0.1f);
		
		transform = new Transform();
		transform.rotateWorld(new Vector3f(0,1,0), 90);
		assertEpsilonEqual(new Vector3f(1,0,0), transform.getViewDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,1,0), transform.getUpDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,0,1), transform.getRightDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,0,1), transform.localDirectionToWorld(new Vector3f(1,0,0)), 0.1f);
		assertEpsilonEqual(new Vector3f(0,1,0), transform.localDirectionToWorld(new Vector3f(0,1,0)), 0.1f);
		assertEpsilonEqual(new Vector3f(1,0,0), transform.localDirectionToWorld(new Vector3f(0,0,-1)), 0.1f);

		transform = new Transform();
		transform.rotate(new Vector3f(1,0,0), 90);
		assertEpsilonEqual(new Vector3f(0,-1,0), transform.getViewDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,0,-1), transform.getUpDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(1,0,0), transform.getRightDirection(), 0.1f);
		
		transform.rotate(new Vector3f(0,1,0), 90);
		assertEpsilonEqual(new Vector3f(0,-1,0), transform.getViewDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(-1,0,0), transform.getUpDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,0,-1), transform.getRightDirection(), 0.1f);
	}

	
	
	
	
	
	
	
	
	
	private void assertEpsilonEqual(Vector3f a, Vector3f b, float delta) {
		Assert.assertEquals(a.x, b.x, delta);
		Assert.assertEquals(a.y, b.y, delta);
		Assert.assertEquals(a.z, b.z, delta);
	}
	private void assertEpsilonEqual(Vector4f a, Vector4f b, float delta) {
		Assert.assertEquals(a.x, b.x, delta);
		Assert.assertEquals(a.y, b.y, delta);
		Assert.assertEquals(a.z, b.z, delta);
		Assert.assertEquals(a.w, b.w, delta);
	}
	
	private boolean quaternionEqualsHelper(Quaternion a, Quaternion b) {
		return (a.x == b.x &&
				a.y == b.y &&
				a.z == b.z &&
				a.w == b.w);
	}
}