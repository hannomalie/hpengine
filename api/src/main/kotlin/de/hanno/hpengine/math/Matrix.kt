package de.hanno.hpengine.math

import de.hanno.hpengine.Transform
import de.hanno.hpengine.camera.Camera
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Quaternionf
import org.joml.Vector3f


fun createPerspective(fovInDegrees: Float, ratio: Float, near: Float, far: Float) = Matrix4f()
    .setPerspective(Math.toRadians(fovInDegrees.toDouble()).toFloat(), ratio, near, far)

fun createOrthogonal(left: Float, right: Float, top: Float, bottom: Float, near: Float, far: Float) = Matrix4f()
    .setOrtho(left, right, bottom, top, near, far)

fun mul(a: Vector3f?, b: Quaternionf?): Vector3f = Vector3f(a).rotate(b)

fun getCubeViewProjectionMatricesForPosition(position: Vector3f): Pair<Array<Matrix4f>, Array<Matrix4f>> {
    val camera = Camera(Transform())
    camera.projectionMatrix = createPerspective(90f, 1f, 0.1f, 250f)
    val projectionMatrix = camera.projectionMatrix
    val resultViewMatrices = arrayOf(Matrix4f(), Matrix4f(), Matrix4f(), Matrix4f(), Matrix4f(), Matrix4f())
    val resultProjectionMatrices = arrayOf(Matrix4f(), Matrix4f(), Matrix4f(), Matrix4f(), Matrix4f(), Matrix4f())
    camera.transform.rotation(Quaternionf().identity())
    camera.transform.rotate(Vector3f(0f, 0f, 1f), 180)
    camera.transform.rotate(Vector3f(0f, 1f, 0f), 90)
    camera.transform.translateLocal(position)
    resultViewMatrices[0] = Matrix4f(camera.viewMatrix)
    resultProjectionMatrices[0] = Matrix4f(projectionMatrix)
    camera.transform.rotation(Quaternionf().identity())
    camera.transform.rotate(Vector3f(0f, 0f, 1f), 180)
    camera.transform.rotate(Vector3f(0f, 1f, 0f), -90)
    camera.transform.translateLocal(position)
    resultViewMatrices[1] = Matrix4f(camera.viewMatrix)
    resultProjectionMatrices[1] = Matrix4f(projectionMatrix)
    camera.transform.rotation(Quaternionf().identity())
    camera.transform.rotate(Vector3f(0f, 0f, 1f), 180)
    camera.transform.rotate(Vector3f(1f, 0f, 0f), 90)
    camera.transform.rotate(Vector3f(0f, 1f, 0f), 180)
    camera.transform.translateLocal(position)
    resultViewMatrices[2] = Matrix4f(camera.viewMatrix)
    resultProjectionMatrices[2] = Matrix4f(projectionMatrix)
    camera.transform.rotation(Quaternionf().identity())
    camera.transform.rotate(Vector3f(0f, 0f, 1f), 180)
    camera.transform.rotate(Vector3f(1f, 0f, 0f), -90)
    camera.transform.rotate(Vector3f(0f, 1f, 0f), 180)
    camera.transform.translateLocal(position)
    resultViewMatrices[3] = Matrix4f(camera.viewMatrix)
    resultProjectionMatrices[3] = Matrix4f(projectionMatrix)
    camera.transform.rotation(Quaternionf().identity())
    camera.transform.rotate(Vector3f(0f, 0f, 1f), 180)
    camera.transform.rotate(Vector3f(0f, 1f, 0f), -180)
    camera.transform.translateLocal(position)
    resultViewMatrices[4] = Matrix4f(camera.viewMatrix)
    resultProjectionMatrices[4] = Matrix4f(projectionMatrix)
    camera.transform.rotation(Quaternionf().identity())
    camera.transform.rotate(Vector3f(0f, 0f, 1f), 180)
    camera.transform.translateLocal(position)
    resultViewMatrices[5] = Matrix4f(camera.viewMatrix)
    resultProjectionMatrices[5] = Matrix4f(projectionMatrix)
    return Pair(resultViewMatrices, resultProjectionMatrices)
}
internal fun Matrix4f.isEqualTo(other: Matrix4fc): Boolean {
    if (java.lang.Float.floatToIntBits(m00()) != java.lang.Float.floatToIntBits(other.m00())) return false
    if (java.lang.Float.floatToIntBits(m01()) != java.lang.Float.floatToIntBits(other.m01())) return false
    if (java.lang.Float.floatToIntBits(m02()) != java.lang.Float.floatToIntBits(other.m02())) return false
    if (java.lang.Float.floatToIntBits(m03()) != java.lang.Float.floatToIntBits(other.m03())) return false
    if (java.lang.Float.floatToIntBits(m10()) != java.lang.Float.floatToIntBits(other.m10())) return false
    if (java.lang.Float.floatToIntBits(m11()) != java.lang.Float.floatToIntBits(other.m11())) return false
    if (java.lang.Float.floatToIntBits(m12()) != java.lang.Float.floatToIntBits(other.m12())) return false
    if (java.lang.Float.floatToIntBits(m13()) != java.lang.Float.floatToIntBits(other.m13())) return false
    if (java.lang.Float.floatToIntBits(m20()) != java.lang.Float.floatToIntBits(other.m20())) return false
    if (java.lang.Float.floatToIntBits(m21()) != java.lang.Float.floatToIntBits(other.m21())) return false
    if (java.lang.Float.floatToIntBits(m22()) != java.lang.Float.floatToIntBits(other.m22())) return false
    if (java.lang.Float.floatToIntBits(m23()) != java.lang.Float.floatToIntBits(other.m23())) return false
    if (java.lang.Float.floatToIntBits(m30()) != java.lang.Float.floatToIntBits(other.m30())) return false
    if (java.lang.Float.floatToIntBits(m31()) != java.lang.Float.floatToIntBits(other.m31())) return false
    if (java.lang.Float.floatToIntBits(m32()) != java.lang.Float.floatToIntBits(other.m32())) return false
    return java.lang.Float.floatToIntBits(m33()) == java.lang.Float.floatToIntBits(other.m33())
}