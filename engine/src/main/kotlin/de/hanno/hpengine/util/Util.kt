package de.hanno.hpengine.util

import de.hanno.hpengine.Transform
import de.hanno.hpengine.camera.Camera
import org.joml.*
import java.lang.Float.floatToIntBits
import java.lang.Math
import java.nio.IntBuffer
import kotlin.math.max

object Util {
    fun createPerspective(fovInDegrees: Float, ratio: Float, near: Float, far: Float) = Matrix4f()
        .setPerspective(Math.toRadians(fovInDegrees.toDouble()).toFloat(), ratio, near, far)

    fun createOrthogonal(left: Float, right: Float, top: Float, bottom: Float, near: Float, far: Float) = Matrix4f()
        .setOrtho(left, right, bottom, top, near, far)

    fun mul(a: Vector3f?, b: Quaternionf?): Vector3f = Vector3f(a).rotate(b)

    fun calculateMipMapCountPlusOne(width: Int, height: Int) = calculateMipMapCount(max(width, height)) + 1

    fun calculateMipMapCount(width: Int, height: Int) = calculateMipMapCount(max(width, height))

    fun calculateMipMapCount(size: Int): Int {
        var maxLength = size
        var count = 0
        while (maxLength >= 1) {
            count++
            maxLength /= 2
        }
        return count
    }

    fun IntBuffer.print(columns: Int, rows: Int): String {
        rewind()
        val builder = StringBuilder()
        var columnCounter = 1
        var rowCounter = 0
        while (columns > 0 && hasRemaining() && rowCounter < rows) {
            builder.append(get())
            builder.append(" ")
            if (columnCounter % columns == 0) {
                builder.append(System.lineSeparator())
                rowCounter++
            }
            columnCounter++
        }
        rewind()
        return builder.toString()
    }

    fun countNewLines(content: String): Int {
        val findStr = "\n"
        return content.split(findStr).dropLastWhile { it.isEmpty() }.toTypedArray().size - 1
    }

    fun getCubeViewProjectionMatricesForPosition(position: Vector3f?): Pair<Array<Matrix4f>, Array<Matrix4f>> {
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

}

internal fun Matrix4f.isEqualTo(other: Matrix4fc): Boolean {
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