package de.hanno.hpengine.util

import org.joml.Matrix4f
import org.joml.Matrix4fc
import java.lang.Float.floatToIntBits

internal fun Matrix4f.isEqualTo(b: Matrix4f): Boolean {
    if (this === b) return true
    val other: Matrix4fc = b
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
