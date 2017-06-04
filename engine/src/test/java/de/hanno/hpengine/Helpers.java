package de.hanno.hpengine;

import junit.framework.Assert;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class Helpers {

	static void assertEpsilonEqual(Vector3f a, Vector3f b, float delta) {
		Assert.assertEquals(a.x(), b.x(), delta);
		Assert.assertEquals(a.y(), b.y(), delta);
		Assert.assertEquals(a.z(), b.z(), delta);
	}

	private static void assertEpsilonEqual(Vector4f a, Vector4f b, float delta) {
		Assert.assertEquals(a.x(), b.x(), delta);
		Assert.assertEquals(a.y(), b.y(), delta);
		Assert.assertEquals(a.z(), b.z(), delta);
		Assert.assertEquals(a.w(), b.w(), delta);
	}

	static void assertEpsilonEqual(Quaternionf a, Quaternionf b, float delta) {
		Assert.assertEquals(a.x(), b.x(), delta);
		Assert.assertEquals(a.y(), b.y(), delta);
		Assert.assertEquals(a.z(), b.z(), delta);
		Assert.assertEquals(a.w(), b.w(), delta);
	}

	static boolean quaternionEqualsHelper(Quaternionf a, Quaternionf b) {
		return (a.x() == b.x() &&
				a.y() == b.y() &&
				a.z() == b.z() &&
				a.w() == b.w());
	}

	public static void assertEpsilonEqual(Matrix4f a, Matrix4f b, float delta) {
		Assert.assertEquals(a.m00(), b.m00(), delta);
		Assert.assertEquals(a.m01(), b.m01(), delta);
		Assert.assertEquals(a.m02(), b.m02(), delta);
		Assert.assertEquals(a.m03(), b.m03(), delta);
		Assert.assertEquals(a.m10(), b.m10(), delta);
		Assert.assertEquals(a.m11(), b.m11(), delta);
		Assert.assertEquals(a.m12(), b.m12(), delta);
		Assert.assertEquals(a.m13(), b.m13(), delta);
		Assert.assertEquals(a.m20(), b.m20(), delta);
		Assert.assertEquals(a.m21(), b.m21(), delta);
		Assert.assertEquals(a.m22(), b.m22(), delta);
		Assert.assertEquals(a.m23(), b.m23(), delta);
		Assert.assertEquals(a.m30(), b.m30(), delta);
		Assert.assertEquals(a.m31(), b.m31(), delta);
		Assert.assertEquals(a.m32(), b.m32(), delta);
		Assert.assertEquals(a.m33(), b.m33(), delta);
		
	}

}
