package de.hanno.hpengine;

import de.hanno.hpengine.engine.transform.SimpleTransform;
import de.hanno.hpengine.engine.transform.Transform;
import junit.framework.Assert;
import org.junit.Test;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import de.hanno.hpengine.util.Util;

import javax.vecmath.AxisAngle4f;
import javax.vecmath.Quat4f;

public class BulletTest {

	@Test
	public void matrixConversionTranslationTest() {
		Matrix4f ourSide = new Matrix4f();
		ourSide.translate(new Vector3f(5, 5, 5), null);
		Vector4f expected = new Vector4f();
		new Vector4f(5,5,5,1).mul(ourSide, expected);
		
		javax.vecmath.Matrix4f theirSide = new javax.vecmath.Matrix4f();
		theirSide.setTranslation(new javax.vecmath.Vector3f(5,5,5));
		
		javax.vecmath.Vector3f actual = new javax.vecmath.Vector3f();
		theirSide.get(actual);

		Assert.assertEquals(expected.x, actual.x);
		Assert.assertEquals(expected.y, actual.y);
		Assert.assertEquals(expected.z, actual.z);
	}
	

	@Test
	public void matrixConversionRotationTest() {
		Quaternionf expected = new Quaternionf();
		expected.fromAxisAngleRad(0, 1, 0, 90);
		
		
		javax.vecmath.Matrix4f theirSide = new javax.vecmath.Matrix4f();
		theirSide.rotY(90);
		javax.vecmath.Quat4f actual = new javax.vecmath.Quat4f();
		theirSide.get(actual);
		
		Assert.assertEquals(expected.x, actual.x, 0.01f);
		Assert.assertEquals(expected.y, actual.y, 0.01f);
		Assert.assertEquals(expected.z, actual.z, 0.01f);
		Assert.assertEquals(expected.w, actual.w, 0.01f);
	}
	
	@Test
	public void quaternionConversionTest() {
		Quaternionf expected = new Quaternionf().identity();

		com.bulletphysics.linearmath.Transform theirSide = new com.bulletphysics.linearmath.Transform();
		theirSide.setIdentity();
		Quat4f actual = new Quat4f();
		theirSide.getRotation(actual);
		actual.normalize();

		Assert.assertEquals(expected.x, actual.x);
		Assert.assertEquals(expected.y, actual.y);
		Assert.assertEquals(expected.z, actual.z);
		Assert.assertEquals(expected.w, actual.w);
	}
	
	@Test
	public void transformConversionTest() {
		Transform tempTransform = new SimpleTransform();
		tempTransform.setTranslation(new Vector3f(2,0,0));
		Quaternionf orientation = new Quaternionf();
		orientation.fromAxisAngleRad(0,1,0,90);
		tempTransform.setOrientation(orientation);
		Matrix4f expected = tempTransform.getTransformation();
		
		com.bulletphysics.linearmath.Transform temp = Util.toBullet(tempTransform);
		
		Quat4f quat = new javax.vecmath.Quat4f();
		quat.set(new AxisAngle4f(0,1,0,90));
		com.bulletphysics.linearmath.Transform actualTransform = new com.bulletphysics.linearmath.Transform(
			new javax.vecmath.Matrix4f(quat, new javax.vecmath.Vector3f(2,0,0), 1f));
		
		float[] m = new float[16];
		actualTransform.getOpenGLMatrix(m);
		Matrix4f actual = new Matrix4f();
		actual.m00(m[0]);
		actual.m01(m[1]);
		actual.m02(m[2]);
		actual.m03(m[3]);
		actual.m10(m[4]);
		actual.m11(m[5]);
		actual.m12(m[6]);
		actual.m13(m[7]);
		actual.m20(m[8]);
		actual.m21(m[9]);
		actual.m22(m[10]);
		actual.m23(m[11]);
		actual.m30(m[12]);
		actual.m31(m[13]);
		actual.m32(m[14]);
		actual.m33(m[15]);

		Assert.assertEquals(expected.m00(), actual.m00(), 0.01);
		Assert.assertEquals(expected.m01(), actual.m01(), 0.01);
		Assert.assertEquals(expected.m02(), actual.m02(), 0.01);
		Assert.assertEquals(expected.m03(), actual.m03(), 0.01);
		Assert.assertEquals(expected.m10(), actual.m10(), 0.01);
		Assert.assertEquals(expected.m11(), actual.m11(), 0.01);
		Assert.assertEquals(expected.m12(), actual.m12(), 0.01);
		Assert.assertEquals(expected.m13(), actual.m13(), 0.01);
		Assert.assertEquals(expected.m20(), actual.m20(), 0.01);
		Assert.assertEquals(expected.m21(), actual.m21(), 0.01);
		Assert.assertEquals(expected.m22(), actual.m22(), 0.01);
		Assert.assertEquals(expected.m23(), actual.m23(), 0.01);
		Assert.assertEquals(expected.m30(), actual.m30(), 0.01);
		Assert.assertEquals(expected.m31(), actual.m31(), 0.01);
		Assert.assertEquals(expected.m32(), actual.m32(), 0.01);
		Assert.assertEquals(expected.m33(), actual.m33(), 0.01);
	}
	
	
	
}
