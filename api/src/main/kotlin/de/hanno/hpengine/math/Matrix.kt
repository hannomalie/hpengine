package de.hanno.hpengine.math

import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Quaternionf
import org.joml.Vector3f
import java.lang.Float.floatToIntBits


fun createPerspective(fovInDegrees: Float, ratio: Float, near: Float, far: Float) = Matrix4f()
    .setPerspective(Math.toRadians(fovInDegrees.toDouble()).toFloat(), ratio, near, far)

fun createOrthogonal(left: Float, right: Float, top: Float, bottom: Float, near: Float, far: Float) = Matrix4f()
    .setOrtho(left, right, bottom, top, near, far)

fun mul(a: Vector3f?, b: Quaternionf?): Vector3f = Vector3f(a).rotate(b)

fun Matrix4f.isEqualTo(other: Matrix4fc): Boolean {
    if (floatToIntBits(m00()) != floatToIntBits(other.m00())) return false
    if (floatToIntBits(m01()) != floatToIntBits(other.m01())) return false
    if (floatToIntBits(m02()) != floatToIntBits(other.m02())) return false
    if (floatToIntBits(m03()) != floatToIntBits(other.m03())) return false
    if (floatToIntBits(m10()) != floatToIntBits(other.m10())) return false
    if (floatToIntBits(m11()) != floatToIntBits(other.m11())) return false
    if (floatToIntBits(m12()) != floatToIntBits(other.m12())) return false
    if (floatToIntBits(m13()) != floatToIntBits(other.m13())) return false
    if (floatToIntBits(m20()) != floatToIntBits(other.m20())) return false
    if (floatToIntBits(m21()) != floatToIntBits(other.m21())) return false
    if (floatToIntBits(m22()) != floatToIntBits(other.m22())) return false
    if (floatToIntBits(m23()) != floatToIntBits(other.m23())) return false
    if (floatToIntBits(m30()) != floatToIntBits(other.m30())) return false
    if (floatToIntBits(m31()) != floatToIntBits(other.m31())) return false
    if (floatToIntBits(m32()) != floatToIntBits(other.m32())) return false
    return floatToIntBits(m33()) == floatToIntBits(other.m33())
}