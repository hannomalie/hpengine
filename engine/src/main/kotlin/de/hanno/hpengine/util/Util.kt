package de.hanno.hpengine.util

import java.io.IOException
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.lang.StringBuilder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.Transform
import org.joml.*
import java.io.File
import java.io.InputStream
import java.lang.Float.floatToIntBits
import java.lang.IllegalArgumentException
import java.lang.Math
import java.nio.ByteBuffer
import java.util.*
import java.util.logging.Logger
import javax.vecmath.Quat4f

object Util {
    private val LOGGER = Logger.getLogger(Util::class.java.name)
    private const val PI = 3.14159265358979323846
    private const val VECTOR_DELIMITER = "_"
    fun loadAsTextFile(path: String?): String {
        //InputStream in = Util.class.getClass().getResourceAsStream("/de/hanno/render/de.hanno.hpengine.shader/vs.vs");
        val `in` = Util::class.java.javaClass.getResourceAsStream(path)
        val result = convertStreamToString(`in`)
        try {
            `in`.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return result
    }

    fun getFileExtension(`in`: String): String {
        var extension = ""
        val i = `in`.lastIndexOf('.')
        if (i > 0) {
            extension = `in`.substring(i + 1)
        }
        return extension
    }

    fun toImage(byteBuffer: ByteBuffer, width: Int, height: Int): BufferedImage {
        val pixels = IntArray(width * height)
        var index: Int
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (i in pixels.indices) {
            index = i * 3
            pixels[i] = (byteBuffer[index].toInt() shl 16) +
                    (byteBuffer[index + 1].toInt() shl 8) +
                    (byteBuffer[index + 2].toInt() shl 0)
        }
        //Allocate colored pixel to buffered Image
        image.setRGB(0, 0, width, height, pixels, 0, width)

//		try {
//			image = ImageIO.read(in);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
        return image
    }

    fun saveImage(image: BufferedImage?, path: String?) {
        if (image == null) return
        try {
            ImageIO.write(image, "png", File(path))
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun convertStreamToString(`is`: InputStream): String {
        val s = Scanner(`is`).useDelimiter("\\A")
        return if (s.hasNext()) s.next() else ""
    }

    fun coTangent(angle: Float): Float {
        return (1f / Math.tan(angle.toDouble())).toFloat()
    }

    fun degreesToRadians(degrees: Float): Float {
        return degrees * (PI / 180.0).toFloat()
    }

    fun createPerspective(fovInDegrees: Float, ratio: Float, near: Float, far: Float): Matrix4f {
        return Matrix4f().setPerspective(Math.toRadians(fovInDegrees.toDouble()).toFloat(), ratio, near, far)
    }

    fun createOrthogonal(left: Float, right: Float, top: Float, bottom: Float, near: Float, far: Float): Matrix4f {
        return Matrix4f().setOrtho(left, right, bottom, top, near, far)
    }

    fun lookAt(eye: Vector3f?, center: Vector3f?, up: Vector3f?): Matrix4f {
        return Matrix4f().lookAt(eye, center, up)
        //        Vector3f f = new Vector3f();
//        Vector3f.sub(center, eye, f);
//        f.normalize();
//
//        Vector3f u = new Vector3f();
//        up.normalise(u);
//        Vector3f s = new Vector3f();
//        Vector3f.cross(f, u, s);
//        s.normalize();
//        Vector3f.cross(s, f, u);
//
//        Matrix4f mat = new Matrix4f();
//
//        mat.m00 = s.x;
//        mat.m10 = s.y;
//        mat.m20 = s.z;
//        mat.m01 = u.x;
//        mat.m11 = u.y;
//        mat.m21 = u.z;
//        mat.m02 = -f.x;
//        mat.m12 = -f.y;
//        mat.m22 = -f.z;
//
//        Matrix4f temp = new Matrix4f();
//        Matrix4f.translate(new Vector3f(-eye.x, -eye.y, -eye.z), mat, temp);
//
//        return mat;
    }

    //	//http://molecularmusings.wordpress.com/2013/05/24/a-faster-quaternion-vector-multiplication/
    //	public static Vector3f _mul(Vector3f a, Quaternionf b) {
    //		Vector3f temp = (Vector3f) Vector3f.cross(new Vector3f(b.x, b.y, b.z), a, null).scale(2f);
    //		Vector3f result = new Vector3f(a, (Vector3f) temp.scale(b.w), null);
    //		result = new Vector3f(result, Vector3f.cross(new Vector3f(b.x,  b.y,  b.z), temp, null), null);
    //		return result;
    //	}
    fun mul(a: Vector3f?, b: Quaternionf?): Vector3f {
        return Vector3f(a).rotate(b)
        //		Quaternionf in = new Quaternionf(a.x,a.y,a.z,0);
//		Quaternionf temp = new Quaternionf((b, in, null);
//		temp = new Quaternionf((temp, conjugate(b), null);
//		return new Vector3f(temp.x, temp.y, temp.z);
    }

    fun toMatrix(q: Quaternionf, out: Matrix4f?): Matrix4f {
        return q[out]
        //		float qx = q.x();
//        float qy = q.y();
//        float qz = q.z();
//        float qw = q.w();
//
//        // just pretend these are superscripts.
//        float qx2 = qx * qx;
//        float qy2 = qy * qy;
//        float qz2 = qz * qz;
//
//        out.m00 = 1 - 2 * qy2 - 2 * qz2;
//        out.m01 = 2 * qx * qy + 2 * qz * qw;
//        out.m02 = 2 * qx * qz - 2 * qy * qw;
//        out.m03 = 0;
//
//        out.m10 = 2 * qx * qy - 2 * qz * qw;
//        out.m11 = 1 - 2 * qx2 - 2 * qz2;
//        out.m12 = 2 * qy * qz + 2 * qx * qw;
//        out.m13 = 0;
//
//        out.m20 = 2 * qx * qz + 2 * qy * qw;
//        out.m21 = 2 * qy * qz - 2 * qx * qw;
//        out.m22 = 1 - 2 * qx2 - 2 * qy2;
//        out.m23 = 0;
//
//        out.m30 = 0;
//        out.m31 = 0;
//        out.m32 = 0;
//
//        return out;
    }

    //	public static Quaternionf slerp(Quaternionf q1, Quaternionf q2, float t) {
    //		Quaternionf qInterpolated = new Quaternionf();
    //
    //		if (q1.equals(q2)) {
    //			return q1;
    //		}
    //
    //		// Temporary array to hold second quaternion.
    //
    //		float cosTheta = q1.x * q2.x + q1.y * q2.y + q1.z * q2.z + q1.w * q2.w;
    //
    //		if (cosTheta < 0.0f) {
    //			// Flip sigh if so.
    //			q2 = conjugate(q2);
    //			cosTheta = -cosTheta;
    //		}
    //
    //		float beta = 1.0f - t;
    //
    //		// Set the first and second scale for the interpolation
    //		float scale0 = 1.0f - t;
    //		float scale1 = t;
    //
    //		if (1.0f - cosTheta > 0.1f) {
    //			// We are using spherical interpolation.
    //			float theta = (float) Math.acos(cosTheta);
    //			float sinTheta = (float) Math.sin(theta);
    //			scale0 = (float) Math.sin(theta * beta) / sinTheta;
    //			scale1 = (float) Math.sin(theta * t) / sinTheta;
    //		}
    //
    //		// Interpolation.
    //		qInterpolated.x = scale0 * q1.x + scale1 * q2.x;
    //		qInterpolated.y = scale0 * q1.y + scale1 * q2.y;
    //		qInterpolated.z = scale0 * q1.z + scale1 * q2.z;
    //		qInterpolated.w = scale0 * q1.w + scale1 * q2.w;
    //
    //		return qInterpolated;
    //	}
    fun conjugate(q: Quaternionf): Quaternionf {
        q.x = -q.x
        q.y = -q.y
        q.z = -q.z
        return q
    }

    fun vectorToString(`in`: Vector3f): String {
        val b = StringBuilder()
        b.append(`in`.x)
        b.append(VECTOR_DELIMITER)
        b.append(`in`.y)
        b.append(VECTOR_DELIMITER)
        b.append(`in`.z)
        return b.toString()
    }

    fun vectorToString(`in`: Vector4f): String {
        val b = StringBuilder()
        b.append(`in`.x)
        b.append(VECTOR_DELIMITER)
        b.append(`in`.y)
        b.append(VECTOR_DELIMITER)
        b.append(`in`.z)
        b.append(VECTOR_DELIMITER)
        b.append(`in`.w)
        return b.toString()
    }

    fun toBullet(`in`: Vector3f): javax.vecmath.Vector3f {
        return javax.vecmath.Vector3f(`in`.x, `in`.y, `in`.z)
    }

    fun fromBullet(`in`: javax.vecmath.Vector3f): Vector3f {
        return Vector3f(`in`.x, `in`.y, `in`.z)
    }

    fun toBullet(`in`: Transform): com.bulletphysics.linearmath.Transform {
        val out = `in`.transformation
        return com.bulletphysics.linearmath.Transform(toBullet(out))
    }

    fun toBullet(`in`: Matrix4f): javax.vecmath.Matrix4f {
        val out = javax.vecmath.Matrix4f()
        out.m00 = `in`.m00()
        out.m01 = `in`.m01()
        out.m02 = `in`.m02()
        out.m03 = `in`.m03()
        out.m10 = `in`.m10()
        out.m11 = `in`.m11()
        out.m12 = `in`.m12()
        out.m13 = `in`.m13()
        out.m20 = `in`.m20()
        out.m21 = `in`.m21()
        out.m22 = `in`.m22()
        out.m23 = `in`.m23()
        out.m30 = `in`.m30()
        out.m31 = `in`.m31()
        out.m32 = `in`.m32()
        out.m33 = `in`.m33()
        out.transpose()
        return out
    }

    fun fromBullet(`in`: javax.vecmath.Matrix4f): Matrix4f {
        val out = Matrix4f()
        out.m00(`in`.m00)
        out.m01(`in`.m01)
        out.m02(`in`.m02)
        out.m03(`in`.m03)
        out.m10(`in`.m10)
        out.m11(`in`.m11)
        out.m12(`in`.m12)
        out.m13(`in`.m13)
        out.m20(`in`.m20)
        out.m21(`in`.m21)
        out.m22(`in`.m22)
        out.m23(`in`.m23)
        out.m30(`in`.m30)
        out.m31(`in`.m31)
        out.m32(`in`.m32)
        out.m33(`in`.m33)
        return out
    }

    fun fromBullet(`in`: com.bulletphysics.linearmath.Transform): Transform {
        val out = javax.vecmath.Matrix4f()
        `in`.getMatrix(out)
        val outRot = Quat4f()
        val finalTransform = Transform()
        finalTransform.setTranslation(Vector3f(`in`.origin.x, `in`.origin.y, `in`.origin.z))
        finalTransform.orientation = fromBullet(`in`.getRotation(outRot))
        return finalTransform
    }

    private fun fromBullet(rotation: Quat4f): Quaternionf {
        return Quaternionf(rotation.x, rotation.y, rotation.z, rotation.w)
    }

    fun calculateMipMapCountPlusOne(width: Int, height: Int): Int {
        return calculateMipMapCount(Math.max(width, height)) + 1
    }

    fun calculateMipMapCount(width: Int, height: Int): Int {
        return calculateMipMapCount(Math.max(width, height))
    }

    fun calculateMipMapCount(size: Int): Int {
        var maxLength = size
        var count = 0
        while (maxLength >= 1) {
            count++
            maxLength /= 2
        }
        return count
    }

    fun printFloatBuffer(values: FloatBuffer) {
        printFloatBuffer(values, 4, 1000)
    }

    @JvmOverloads
    fun printFloatBuffer(buffer: FloatBuffer, columns: Int, rows: Int = 1000): String {
        buffer.rewind()
        val builder = StringBuilder()
        var columnCounter = 1
        var rowCounter = 0
        while (buffer.hasRemaining() && rowCounter < rows) {
            builder.append(buffer.get())
            builder.append(" ")
            if (columnCounter % columns == 0) {
                builder.append(System.lineSeparator())
                rowCounter++
            }
            columnCounter++
        }
        buffer.rewind()
        val result = builder.toString()
        println(result)
        return result
    }

    @JvmOverloads
    fun printIntBuffer(buffer: IntBuffer, columns: Int, rows: Int = 1000): String {
        val asString = toString(buffer, columns, rows)
        println(asString)
        return asString
    }

    fun toString(buffer: IntBuffer, columns: Int, rows: Int): String {
        buffer.rewind()
        val builder = StringBuilder()
        var columnCounter = 1
        var rowCounter = 0
        while (columns > 0 && buffer.hasRemaining() && rowCounter < rows) {
            builder.append(buffer.get())
            builder.append(" ")
            if (columnCounter % columns == 0) {
                builder.append(System.lineSeparator())
                rowCounter++
            }
            columnCounter++
        }
        buffer.rewind()
        return builder.toString()
    }

    fun getArray(values: FloatBuffer): FloatArray {
        val array = FloatArray(values.capacity())
        values[array]
        return array
    }

    fun getOverallMinMax(minMax: Array<Vector4f>, minMaxFromChildren: List<Array<Vector4f>>) {
        for (candidate in minMaxFromChildren) {
            val min = candidate[0]
            val max = candidate[1]
            if (min.x < minMax[0].x) {
                minMax[0].x = min.x
            }
            if (min.y < minMax[0].y) {
                minMax[0].y = min.y
            }
            if (min.z < minMax[0].z) {
                minMax[0].z = min.z
            }
            if (max.x > minMax[1].x) {
                minMax[1].x = min.x
            }
            if (max.y > minMax[1].y) {
                minMax[1].y = min.y
            }
            if (max.z > minMax[1].z) {
                minMax[1].z = min.z
            }
        }
    }

    fun <T> toArray(values: Collection<T>, clazz: Class<T>?): Array<T> {
        val array = java.lang.reflect.Array.newInstance(clazz, values.size) as Array<T>
        val iterator = values.iterator()
        var counter = 0
        while (iterator.hasNext()) {
            array[counter] = iterator.next()
            counter++
        }
        return array
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

    fun equals(a: Quaternionf, b: Quaternionf): Boolean {
        return a.x == b.x && a.y == b.y && a.x == b.x && a.w == b.w
    }

    fun translateGLErrorString(error_code: Int): String = when (error_code) {
        0 -> "No error"
        1280 -> "Invalid enum"
        1281 -> "Invalid value"
        1282 -> "Invalid operation"
        1283 -> "Stack overflow"
        1284 -> "Stack underflow"
        1285 -> "Out of memory"
        1286 -> "Invalid framebuffer operation"
        32817 -> "Table too large"
        else -> throw IllegalArgumentException("Can't translate error code $error_code")
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