package test;

import camera.Camera;
import camera.Frustum;
import component.CameraComponent;
import engine.model.Entity;
import junit.framework.Assert;
import org.junit.Test;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import scene.AABB;
import util.Util;

public class CameraTest extends TestWithRenderer {
	
	@Test
	public void rotation() {
		Entity camera = renderer.getEntityFactory().getEntity().addComponent(new CameraComponent(new Camera(renderer)));
		Assert.assertEquals(new Vector3f(0,1,0), camera.getUpDirection());
		
		camera.rotate(new Vector3f(0,1,0), 90f);

		float epsilon = 0.01f;
		Assert.assertEquals(0, camera.getUpDirection().x, epsilon);
		Assert.assertEquals(1, camera.getUpDirection().y, epsilon);
		Assert.assertEquals(0, camera.getUpDirection().z, epsilon);

		Assert.assertEquals(0, camera.getRightDirection().x, epsilon);
		Assert.assertEquals(0, camera.getRightDirection().y, epsilon);
		Assert.assertEquals(1, camera.getRightDirection().z, epsilon);
		
		Assert.assertEquals(1, ((Vector3f)(camera.getViewDirection())).x, epsilon); // z is 1, not -1!
		Assert.assertEquals(0, ((Vector3f)(camera.getViewDirection())).y, epsilon);
		Assert.assertEquals(0, ((Vector3f)(camera.getViewDirection())).z, epsilon);
	}
	
	@Test
	public void inFrustum() {
		Matrix4f projectionMatrix = Util.createPerpective(60, 16/9, 0.1f, 100f);
		Camera camera = new Camera(renderer, projectionMatrix, 0.1f, 100f, 60, 16/9);
		Entity cameraEntity = renderer.getEntityFactory().getEntity().addComponent(new CameraComponent(camera));
		Assert.assertEquals(new Vector3f(0,0,-1), ((Vector3f)(cameraEntity.getViewDirection())));
		
		Frustum frustum = camera.getFrustum();

		Assert.assertEquals(-0.1f, frustum.values[Frustum.FRONT][Frustum.D], 0.0001f); // z is negative, again...
		Assert.assertEquals(100f, frustum.values[Frustum.BACK][Frustum.D], 0.0001f); // distance is always positive value
		Assert.assertFalse(frustum.pointInFrustum(0, 0, 1));
		Assert.assertTrue(frustum.pointInFrustum(0, 0, -1));

		cameraEntity.moveInWorld(new Vector3f(0,0,-5));
		frustum.calculate(camera);
		Helpers.assertEpsilonEqual(new Vector3f(0, 0, -1), cameraEntity.getViewDirection(), 0.01f);
		Assert.assertTrue(frustum.pointInFrustum(0, 0, 1));

		cameraEntity.setPosition(new Vector3f());
		cameraEntity.rotateWorld(new Vector3f(0, 1, 0), 180); // cam is now at pos 0,0,0, rotated 180 degrees, looking in +z
		Helpers.assertEpsilonEqual(new Vector3f(0, 0, 1), cameraEntity.getViewDirection(), 0.01f);
		Assert.assertTrue(frustum.pointInFrustum(0, 0, 1));
		Assert.assertTrue(frustum.sphereInFrustum(0, 0, 1, 1));
		Assert.assertTrue(frustum.cubeInFrustum(new Vector3f(0, 0, 1), 1));
		Assert.assertTrue(frustum.boxInFrustum(new AABB(new Vector3f(0, 0, 1), 1)));

		frustum.calculate(camera);
		Assert.assertFalse(frustum.pointInFrustum(0, 0, -1));
		Assert.assertFalse(frustum.sphereInFrustum(0, 0, -2, 1));
		Assert.assertFalse(frustum.cubeInFrustum(new Vector3f(0, 0, -2), 1));
		Assert.assertFalse(frustum.boxInFrustum(new AABB(new Vector3f(0, 0, -2), 1)));
		Assert.assertFalse(new AABB(new Vector3f(0, 0, -2), 1).isInFrustum(camera));
		Assert.assertTrue(new AABB(new Vector3f(0, 0, 0), 10).isInFrustum(camera));
		
	}
}