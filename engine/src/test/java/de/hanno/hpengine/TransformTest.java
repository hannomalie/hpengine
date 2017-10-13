package de.hanno.hpengine;

import de.hanno.hpengine.engine.transform.SimpleTransform;
import de.hanno.hpengine.engine.transform.Transform;
import junit.framework.Assert;
import org.joml.*;
import org.junit.Ignore;
import org.junit.Test;

import java.lang.Math;
import java.util.Random;

public class TransformTest {
	@Test
	public void initTest() {
		Transform transform = new SimpleTransform();
		Assert.assertEquals(new Vector3f(), transform.getPosition());
		Assert.assertTrue(quaternionEqualsHelper(new Quaternionf(), transform.getOrientation()));
		Assert.assertEquals(new Vector3f(1,1,1), transform.getScale());

		quaternionEqualsHelper(new Quaternionf().identity(), new Quaternionf());
	}

	@Test
	public void directionsTest() {
		Transform transform = new SimpleTransform();
		assertEpsilonEqual(Transform.WORLD_VIEW, transform.getViewDirection(), 0.1f);
		assertEpsilonEqual(Transform.WORLD_RIGHT, transform.getRightDirection(), 0.1f);
		assertEpsilonEqual(Transform.WORLD_UP, transform.getUpDirection(), 0.1f);
	}

	@Test
	public void translationTest() {
		Transform transform = new SimpleTransform();
        transform.translateLocal(new Vector3f(5,5,-5));
        assertEpsilonEqual(new Vector3f(5,5,-5), transform.getPosition(), 0.1f);

		transform = new SimpleTransform();
		transform.rotate(new AxisAngle4f((float) Math.toRadians(90), Transform.WORLD_UP));
		assertEpsilonEqual(new Vector3f(-1,0,0), transform.getViewDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,1,0), transform.getUpDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,0,-1), transform.getRightDirection(), 0.1f);
        transform.translateLocal(new Vector3f(5,5,-5));
        assertEpsilonEqual(new Vector3f(5,5,-5), transform.getPosition(), 0.1f);

        transform.translateLocal(new Vector3f(-5,-5,5));
        assertEpsilonEqual(new Vector3f(), transform.getPosition(), 0.1f);

        transform.translate(new Vector3f(5,5,5));
        assertEpsilonEqual(new Vector3f(5,5,-5), transform.getPosition(), 0.1f);
	}

	@Test
	public void localAndWorldTranslationTest() {
		Transform transformA = new SimpleTransform();
        transformA.translateLocal(new Vector3f(new Random().nextFloat(), new Random().nextFloat(), new Random().nextFloat()));
        transformA.rotate(new AxisAngle4f(new Random().nextFloat(), new Random().nextFloat(), new Random().nextFloat(), new Random().nextFloat()));

		Transform transformB = new SimpleTransform();
		transformB.setTranslation(transformA.getPosition());
		transformB.setOrientation(transformA.getOrientation());

        transformA.translateLocal(new Vector3f(34,0,0));
        transformB.translateLocal(transformB.getRightDirection().mul(34));

        assertEpsilonEqual(transformA.getPosition(), transformB.getPosition(), 0.01f);
		assertEpsilonEqual(transformA.getOrientation(), transformB.getOrientation(), 0.01f);
	}

	@Test
	public void localAndWorldRotationTest() {
		Transform transformA = new SimpleTransform();
        transformA.translateLocal(new Vector3f(new Random().nextFloat(), new Random().nextFloat(), new Random().nextFloat()));
        transformA.rotate(new Vector4f(new Random().nextFloat(), new Random().nextFloat(), new Random().nextFloat(), new Random().nextFloat()));

		Transform transformB = new SimpleTransform();
		transformB.setTranslation(transformA.getPosition());
		transformB.setOrientation(transformA.getOrientation());

		transformA.rotate(new Vector3f(1,0,0), 90);
		transformB.rotate(new Vector3f(1,0,0), 90);

		assertEpsilonEqual(transformA.getPosition(), transformB.getPosition(), 0.01f);
		assertEpsilonEqual(transformA.getOrientation(), transformB.getOrientation(), 0.01f);
	}

	@Test
	public void rotationTest() {
		Transform transform = new SimpleTransform();
		transform.rotate(new Vector3f(0,1,0), 90);
		assertEpsilonEqual(new Vector3f(-1,0,0), transform.getViewDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,1,0), transform.getUpDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,0,-1), transform.getRightDirection(), 0.1f);

		transform.rotate(new Vector3f(0,1,0), -90);
		assertEpsilonEqual(Transform.WORLD_VIEW, transform.getViewDirection(), 0.1f);
		assertEpsilonEqual(Transform.WORLD_RIGHT, transform.getRightDirection(), 0.1f);
		assertEpsilonEqual(Transform.WORLD_UP, transform.getUpDirection(), 0.1f);

		transform = new SimpleTransform();
		transform.rotate(new Vector3f(1,0,0), 90);
		assertEpsilonEqual(new Vector3f(0,1,0), transform.getViewDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(1,0,0), transform.getRightDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,0,1), transform.getUpDirection(), 0.1f);

		transform = new SimpleTransform();
		transform.rotate(new Vector3f(0,0,1), -90);
		assertEpsilonEqual(new Vector3f(0,0,-1), transform.getViewDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(1,0,0), transform.getUpDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,-1,0), transform.getRightDirection(), 0.1f);

		transform = new SimpleTransform();
		transform.rotate(new Vector3f(0,1,0), 90);
		assertEpsilonEqual(new Vector3f(-1,0,0), transform.getViewDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,1,0), transform.getUpDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,0,-1), transform.getRightDirection(), 0.1f);

		transform = new SimpleTransform();
		transform.rotate(new Vector3f(1,0,0), 90);
		assertEpsilonEqual(new Vector3f(0,1,0), transform.getViewDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,0,1), transform.getUpDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(1,0,0), transform.getRightDirection(), 0.1f);

        transform.rotate(new Vector3f(0,1,0), 90);
        assertEpsilonEqual(new Vector3f(-1,0,0), transform.getViewDirection(), 0.1f);
        assertEpsilonEqual(new Vector3f(0,0,1), transform.getUpDirection(), 0.1f);
        assertEpsilonEqual(new Vector3f(0,1,0), transform.getRightDirection(), 0.1f);
	}

	@Test
    public void localAndNonLocalRotation() {
        Transform downFacing = new SimpleTransform();
        downFacing.rotate(new Vector3f(1,0,0), -90);
        assertEpsilonEqual(new Vector3f(0,-1,0), downFacing.getViewDirection(), 0.1f);
        assertEpsilonEqual(new Vector3f(0,0,-1), downFacing.getUpDirection(), 0.1f);
        assertEpsilonEqual(new Vector3f(1,0,0), downFacing.getRightDirection(), 0.1f);

        Transform downFacingLocalRotated = new SimpleTransform(downFacing);
        assertEpsilonEqual(new Vector3f(0,-1,0), downFacingLocalRotated.getViewDirection(), 0.1f);
        assertEpsilonEqual(new Vector3f(0,0,-1), downFacingLocalRotated.getUpDirection(), 0.1f);
        assertEpsilonEqual(new Vector3f(1,0,0), downFacingLocalRotated.getRightDirection(), 0.1f);
        downFacingLocalRotated.rotateLocal((float) Math.toRadians(90), 0, 1, 0);
        assertEpsilonEqual(new Vector3f(0,-1,0), downFacingLocalRotated.getViewDirection(), 0.1f);
        assertEpsilonEqual(new Vector3f(-1,0,0), downFacingLocalRotated.getUpDirection(), 0.1f);
        assertEpsilonEqual(new Vector3f(0,0,-1), downFacingLocalRotated.getRightDirection(), 0.1f);

        Transform downFacingNonLocalRotated = new SimpleTransform(downFacing);
        assertEpsilonEqual(new Vector3f(0,-1,0), downFacingNonLocalRotated.getViewDirection(), 0.1f);
        assertEpsilonEqual(new Vector3f(0,0,-1), downFacingNonLocalRotated.getUpDirection(), 0.1f);
        assertEpsilonEqual(new Vector3f(1,0,0), downFacingNonLocalRotated.getRightDirection(), 0.1f);
        downFacingNonLocalRotated.rotate((float) Math.toRadians(90), 0, 1, 0);
        assertEpsilonEqual(new Vector3f(-1,0,0), downFacingNonLocalRotated.getViewDirection(), 0.1f);
        assertEpsilonEqual(new Vector3f(0,0,-1), downFacingNonLocalRotated.getUpDirection(), 0.1f);
        assertEpsilonEqual(new Vector3f(0,-1,0), downFacingNonLocalRotated.getRightDirection(), 0.1f);

    }

	@Test
	public void translationRotationTest() {
		Transform transform = new SimpleTransform();
        transform.translateLocal(new Vector3f(10, 10, 10));
        transform.rotate(new Vector3f(1,0,0), 90);
		transform.rotate(new Vector3f(0,1,0), 55);
		assertEpsilonEqual(new Vector3f(10, 10, 10), transform.getPosition(), 0.1f);
	}


	@Test
	public void multipleRotationsTest() {
		Transform transform = new SimpleTransform();
        transform.translateLocal(new Vector3f(10, 10, 10));
        transform.rotate(new Vector3f(1,0,0), 90);
		assertEpsilonEqual(new Vector3f(0,-1,0), transform.getViewDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,0,1), transform.getUpDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(1,0,0), transform.getRightDirection(), 0.1f);

		transform.rotate(new Vector3f(0,1,0), 90);
		assertEpsilonEqual(new Vector3f(1,0,0), transform.getViewDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,0,1), transform.getUpDirection(), 0.1f);
		assertEpsilonEqual(new Vector3f(0,1,0), transform.getRightDirection(), 0.1f);
	}

	@Test
	public void simpleTranslationWithParent() {
		Transform parent = new SimpleTransform();
		Transform child = new SimpleTransform();
		child.setParent(parent);

        parent.translateLocal(new Vector3f(0, 5, 0));

        assertEpsilonEqual(new Vector3f(0, 5, 0), parent.getPosition());
		assertEpsilonEqual(new Vector3f(0, 0, 0), child.getPosition());
		assertEpsilonEqual(new Vector3f(0, 5, 0), child.getPosition());
		assertEpsilonEqual(parent.getViewDirection(), child.getViewDirection());

        child.translateLocal(new Vector3f(0, 10, 0));
        assertEpsilonEqual(new Vector3f(0, 10, 0), child.getPosition());
		assertEpsilonEqual(new Vector3f(0, 15, 0), child.getPosition());
	}

	@Test
	public void simpleTranslationRotationWithParent() {
		Transform parent = new SimpleTransform();
		Transform child = new SimpleTransform();
		child.setParent(parent);

        parent.translateLocal(new Vector3f(0, 5, 0));

        assertEpsilonEqual(new Vector3f(0, 5, 0), parent.getPosition());
		assertEpsilonEqual(new Vector3f(0, 0, 0), child.getPosition());
		assertEpsilonEqual(new Vector3f(0, 5, 0), child.getPosition());
		assertEpsilonEqual(parent.getViewDirection(), child.getViewDirection());

		child.rotate(new Vector4f(0, 1, 0, 90));
		assertEpsilonEqual(new Vector3f(1, 0, 0), child.getViewDirection());

        child.translateLocal(new Vector3f(-10, 0, 0));
        assertEpsilonEqual(new Vector3f(-10, 5, 0), child.getPosition());
	}
	@Test
	public void simpleViewMatrixTestWithTranslation() {
		Transform camera = new SimpleTransform();
        camera.translateLocal(new Vector3f(0, 5, 0));

        Vector4f vectorInViewSpace = new Vector4f(0, 5, 0, 1).mul(camera.getViewMatrix());
		assertEpsilonEqual(new Vector4f(0,0,0,1), vectorInViewSpace);
	}
	@Test
	public void simpleViewMatrixTestWithRotation() {
		Transform camera = new SimpleTransform();
		camera.rotate(new Vector4f(0, 1, 0, 90));
		assertEpsilonEqual(new Vector3f(1, 0, 0), camera.getViewDirection());

		Vector4f vectorInViewSpace = new Vector4f(5, 0, 0, 1).mul(camera.getViewMatrix());
		assertEpsilonEqual(new Vector4f(0,0,5,1), vectorInViewSpace);
	}

	@Ignore
	@Test
	public void simpleViewMatrixTestWithParent() {
		Transform parent = new SimpleTransform();
		Transform camera = new SimpleTransform();
		camera.setParent(parent);

        parent.translateLocal(new Vector3f(0, 5, 0));

        assertEpsilonEqual(new Vector3f(0, 5, 0), parent.getPosition());
		assertEpsilonEqual(new Vector3f(0, 0, 0), camera.getPosition());
		assertEpsilonEqual(new Vector3f(0, 5, 0), camera.getPosition());
		assertEpsilonEqual(parent.getViewDirection(), camera.getViewDirection());

		camera.rotate(new Vector4f(0, 1, 0, 90));
		assertEpsilonEqual(new Vector3f(1, 0, 0), camera.getViewDirection());

        camera.translateLocal(new Vector3f(-10, 0, 0));
        assertEpsilonEqual(new Vector3f(-10, 5, 0), camera.getPosition());

		Vector4f vectorInViewSpace = new Vector4f(0, 5, 0, 1).mul(camera.getViewMatrix());
	}


	private void assertEpsilonEqual(Vector3f actual, Vector3f expected) {
		assertEpsilonEqual(actual, expected, 0.01f);
	}
	private void assertEpsilonEqual(Vector3f expected, Vector3f actual, float delta) {
		Assert.assertEquals("x de.hanno.hpengine.component, expected: " + expected + ", actual: " + actual, expected.x, actual.x, delta);
		Assert.assertEquals("y de.hanno.hpengine.component, expected: " + expected + ", actual: " + actual, expected.y, actual.y, delta);
		Assert.assertEquals("z de.hanno.hpengine.component, expected: " + expected + ", actual: " + actual, expected.z, actual.z, delta);
	}
	private void assertEpsilonEqual(Vector4f expected, Vector4f actual) {
		assertEpsilonEqual(expected, actual, 0.001f);
	}
	private void assertEpsilonEqual(Vector4f expected, Vector4f actual, float delta) {
		Assert.assertEquals("x de.hanno.hpengine.component, expected: " + expected + ", actual: " + actual, expected.x, actual.x, delta);
		Assert.assertEquals("y de.hanno.hpengine.component, expected: " + expected + ", actual: " + actual, expected.y, actual.y, delta);
		Assert.assertEquals("z de.hanno.hpengine.component, expected: " + expected + ", actual: " + actual, expected.z, actual.z, delta);
		Assert.assertEquals("w de.hanno.hpengine.component, expected: " + expected + ", actual: " + actual, expected.w, actual.w, delta);
	}

	private void assertEpsilonEqual(Quaternionf expected, Quaternionf actual, float delta) {
		Assert.assertEquals("x de.hanno.hpengine.component, expected: " + expected + ", actual: " + actual, actual.x, actual.x, delta);
		Assert.assertEquals("y de.hanno.hpengine.component, expected: " + expected + ", actual: " + actual, actual.y, actual.y, delta);
		Assert.assertEquals("z de.hanno.hpengine.component, expected: " + expected + ", actual: " + actual, actual.z, actual.z, delta);
		Assert.assertEquals("w de.hanno.hpengine.component, expected: " + expected + ", actual: " + actual, actual.w, actual.w, delta);
	}
	private boolean quaternionEqualsHelper(Quaternionf a, Quaternionf b) {
		return (a.x == b.x &&
				a.y == b.y &&
				a.z == b.z &&
				a.w == b.w);
	}
}
