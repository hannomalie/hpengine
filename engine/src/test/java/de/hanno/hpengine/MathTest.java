package de.hanno.hpengine;

import junit.framework.Assert;
import org.junit.Test;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import de.hanno.hpengine.util.Util;

public class MathTest {
	float delta = 0.1f;

	@Test
	public void QuaternionFromAxisAngle() {
		Quaternionf rot = new Quaternionf();
		rot.fromAxisAngleRad(1, 0, 0, (float) Math.toRadians(90f));
		
		Quaternionf ninetyDegreesXAxis = new Quaternionf((float)Math.sqrt(0.5f), 0, 0, (float)Math.sqrt(0.5f));

		Assert.assertEquals(ninetyDegreesXAxis.x, rot.x, delta);
		Assert.assertEquals(ninetyDegreesXAxis.y, rot.y, delta);
		Assert.assertEquals(ninetyDegreesXAxis.z, rot.z, delta);
		Assert.assertEquals(ninetyDegreesXAxis.w, rot.w, delta);
	}

	@Test
	public void QuaternionRotation() {
		Quaternionf ninetyDegreesXAxis = new Quaternionf();
		ninetyDegreesXAxis.fromAxisAngleRad(1, 0, 0, (float) Math.toRadians(90f));

		Matrix4f tempRotationMatrix = new Matrix4f();
		Matrix4f rotMatrix = Util.toMatrix(ninetyDegreesXAxis, tempRotationMatrix);
		
		Assert.assertEquals(1.0f, rotMatrix.m00(), delta);
		Assert.assertEquals(0f, rotMatrix.m10(), delta);
		Assert.assertEquals(0f, rotMatrix.m20(), delta);
		Assert.assertEquals(0f, rotMatrix.m30(), delta);

		Assert.assertEquals(0f, rotMatrix.m01(), delta);
		Assert.assertEquals(0f, rotMatrix.m11(), delta);
		Assert.assertEquals(-1f, rotMatrix.m21(), delta);
		Assert.assertEquals(0f, rotMatrix.m31(), delta);

		Assert.assertEquals(0f, rotMatrix.m02(), delta);
		Assert.assertEquals(1f, rotMatrix.m12(), delta);
		Assert.assertEquals(0f, rotMatrix.m22(), delta);
		Assert.assertEquals(0f, rotMatrix.m32(), delta);
		
		Assert.assertEquals(0f, rotMatrix.m03(), delta);
		Assert.assertEquals(0f, rotMatrix.m13(), delta);
		Assert.assertEquals(0f, rotMatrix.m23(), delta);
		Assert.assertEquals(1f, rotMatrix.m33(), delta);
	}
	@Test
	public void QuaternionRotation2() {
		Quaternionf hundretAndEightyDegreesYAxis = new Quaternionf();
		hundretAndEightyDegreesYAxis.fromAxisAngleRad(0, 1, 0, (float) Math.toRadians(180f));

		Matrix4f tempRotationMatrix = new Matrix4f();
		Matrix4f rotMatrix = Util.toMatrix(hundretAndEightyDegreesYAxis, tempRotationMatrix);
		
		Assert.assertEquals(-1.0f, rotMatrix.m00(), delta);
		Assert.assertEquals(0f, rotMatrix.m10(), delta);
		Assert.assertEquals(0f, rotMatrix.m20(), delta);
		Assert.assertEquals(0f, rotMatrix.m30(), delta);

		Assert.assertEquals(0f, rotMatrix.m01(), delta);
		Assert.assertEquals(1f, rotMatrix.m11(), delta);
		Assert.assertEquals(0f, rotMatrix.m21(), delta);
		Assert.assertEquals(0f, rotMatrix.m31(), delta);

		Assert.assertEquals(0f, rotMatrix.m02(), delta);
		Assert.assertEquals(0f, rotMatrix.m12(), delta);
		Assert.assertEquals(-1f, rotMatrix.m22(), delta);
		Assert.assertEquals(0f, rotMatrix.m32(), delta);
		
		Assert.assertEquals(0f, rotMatrix.m03(), delta);
		Assert.assertEquals(0f, rotMatrix.m13(), delta);
		Assert.assertEquals(0f, rotMatrix.m23(), delta);
		Assert.assertEquals(1f, rotMatrix.m33(), delta);
	}

//	@Test
//	public void slerpTest() {
//		Quaternionf q1 = new Quaternionf();
//		q1.setFromAxisAngle(new Vector4f(1,0,0,0));
//		
//		Quaternionf q2 = new Quaternionf();
//		q2.setFromAxisAngle(new Vector4f(1,0,0,360));
//
//		Quaternionf half = new Quaternionf();
//		half.setFromAxisAngle(new Vector4f(1,0,0,180));
//		Assert.assertEquals(half, Util.slerp(q1, q2, 0.5f));
//	}
}
