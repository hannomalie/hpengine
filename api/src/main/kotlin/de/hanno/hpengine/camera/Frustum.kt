package de.hanno.hpengine.camera

import de.hanno.hpengine.transform.AABB
import org.joml.FrustumIntersection
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import java.nio.FloatBuffer
import kotlin.math.max

class Frustum {
    /**
     * Pass side of the frustum and x,y,z,w selector to get value
     */
    var values = Array(6) { FloatArray(4) }

    private var buffer = BufferUtils.createFloatBuffer(4 * 6)
    private val proj = FloatArray(16)
    private val modl = FloatArray(16)
    private val clip = FloatArray(16)

    private var buf = BufferUtils.createFloatBuffer(16)
    private val temp = Matrix4f()
    val frustumIntersection = FrustumIntersection()

    fun calculate(projectionMatrix: Matrix4f, viewMatrix: Matrix4f) {
        buf.rewind()
        projectionMatrix[0, buf]
        buf.rewind()
        buf[proj]
        buf.rewind()
        viewMatrix[0, buf]
        buf.rewind()
        buf[modl]
        clip[0] = modl[0] * proj[0] + modl[1] * proj[4] + modl[2] * proj[8] + modl[3] * proj[12]
        clip[1] = modl[0] * proj[1] + modl[1] * proj[5] + modl[2] * proj[9] + modl[3] * proj[13]
        clip[2] = modl[0] * proj[2] + modl[1] * proj[6] + modl[2] * proj[10] + modl[3] * proj[14]
        clip[3] = modl[0] * proj[3] + modl[1] * proj[7] + modl[2] * proj[11] + modl[3] * proj[15]
        clip[4] = modl[4] * proj[0] + modl[5] * proj[4] + modl[6] * proj[8] + modl[7] * proj[12]
        clip[5] = modl[4] * proj[1] + modl[5] * proj[5] + modl[6] * proj[9] + modl[7] * proj[13]
        clip[6] = modl[4] * proj[2] + modl[5] * proj[6] + modl[6] * proj[10] + modl[7] * proj[14]
        clip[7] = modl[4] * proj[3] + modl[5] * proj[7] + modl[6] * proj[11] + modl[7] * proj[15]
        clip[8] = modl[8] * proj[0] + modl[9] * proj[4] + modl[10] * proj[8] + modl[11] * proj[12]
        clip[9] = modl[8] * proj[1] + modl[9] * proj[5] + modl[10] * proj[9] + modl[11] * proj[13]
        clip[10] = modl[8] * proj[2] + modl[9] * proj[6] + modl[10] * proj[10] + modl[11] * proj[14]
        clip[11] = modl[8] * proj[3] + modl[9] * proj[7] + modl[10] * proj[11] + modl[11] * proj[15]
        clip[12] = modl[12] * proj[0] + modl[13] * proj[4] + modl[14] * proj[8] + modl[15] * proj[12]
        clip[13] = modl[12] * proj[1] + modl[13] * proj[5] + modl[14] * proj[9] + modl[15] * proj[13]
        clip[14] = modl[12] * proj[2] + modl[13] * proj[6] + modl[14] * proj[10] + modl[15] * proj[14]
        clip[15] = modl[12] * proj[3] + modl[13] * proj[7] + modl[14] * proj[11] + modl[15] * proj[15]


        // This will extract the RIGHT side of the frustum
        values[RIGHT][A] = clip[3] - clip[0]
        values[RIGHT][B] = clip[7] - clip[4]
        values[RIGHT][C] = clip[11] - clip[8]
        values[RIGHT][D] = clip[15] - clip[12]

        // Now that we have a normal (A,B,C) and a distance (D) to the plane,
        // we want to normalize that normal and distance.

        // Normalize the RIGHT side
        normalizePlane(values, RIGHT)

        // This will extract the LEFT side of the frustum
        values[LEFT][A] = clip[3] + clip[0]
        values[LEFT][B] = clip[7] + clip[4]
        values[LEFT][C] = clip[11] + clip[8]
        values[LEFT][D] = clip[15] + clip[12]

        // Normalize the LEFT side
        normalizePlane(values, LEFT)

        // This will extract the BOTTOM side of the frustum
        values[BOTTOM][A] = clip[3] + clip[1]
        values[BOTTOM][B] = clip[7] + clip[5]
        values[BOTTOM][C] = clip[11] + clip[9]
        values[BOTTOM][D] = clip[15] + clip[13]

        // Normalize the BOTTOM side
        normalizePlane(values, BOTTOM)

        // This will extract the TOP side of the frustum
        values[TOP][A] = clip[3] - clip[1]
        values[TOP][B] = clip[7] - clip[5]
        values[TOP][C] = clip[11] - clip[9]
        values[TOP][D] = clip[15] - clip[13]

        // Normalize the TOP side
        normalizePlane(values, TOP)

        // This will extract the BACK side of the frustum
        values[BACK][A] = clip[3] - clip[2]
        values[BACK][B] = clip[7] - clip[6]
        values[BACK][C] = clip[11] - clip[10]
        values[BACK][D] = clip[15] - clip[14]

        // Normalize the BACK side
        normalizePlane(values, BACK)

        // This will extract the FRONT side of the frustum
        values[FRONT][A] = clip[3] + clip[2]
        values[FRONT][B] = clip[7] + clip[6]
        values[FRONT][C] = clip[11] + clip[10]
        values[FRONT][D] = clip[15] + clip[14]

        // Normalize the FRONT side
        normalizePlane(values, FRONT)
        frustumIntersection.set(projectionMatrix.mul(viewMatrix, temp))
    }

    fun normalizePlane(frustum: Array<FloatArray>, side: Int) {
        // Here we calculate the magnitude of the normal to the plane (point A B C)
        // Remember that (A, B, C) is that same thing as the normal's (X, Y, Z).
        // To calculate magnitude you use the equation:  magnitude = sqrt( x^2 + y^2 + z^2)
        val magnitude =
            Math.sqrt((frustum[side][A] * frustum[side][A] + frustum[side][B] * frustum[side][B] + frustum[side][C] * frustum[side][C]).toDouble())
                .toFloat()

        // Then we divide the plane's values by it's magnitude.
        // This makes it easier to work with.
        frustum[side][A] /= magnitude
        frustum[side][B] /= magnitude
        frustum[side][C] /= magnitude
        frustum[side][D] /= magnitude
    }

    // The code below will allow us to make checks within the frustum.  For example,
    // if we want to see if a point, a sphere, or a cube lies inside of the frustum.
    // Because all of our planes point INWARDS (The normals are all pointing inside the frustum)
    // we then can assume that if a point is in FRONT of all of the planes, it's inside.
    ///////////////////////////////// POINT IN FRUSTUM \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*
    /////
    /////   This determines if a point is inside of the frustum
    /////
    ///////////////////////////////// POINT IN FRUSTUM \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*
    fun pointInFrustum(x: Float, y: Float, z: Float): Boolean {
        // Go through all the sides of the frustum
        for (i in 0..5) {
            // Calculate the plane equation and check if the point is behind a side of the frustum
            if (values[i][A] * x + values[i][B] * y + values[i][C] * z + values[i][D] <= 0) {
                // The point was behind a side, so it ISN'T in the frustum
                return false
            }
        }

        // The point was inside of the frustum (In front of ALL the sides of the frustum)
        return true
    }

    ///////////////////////////////// SPHERE IN FRUSTUM \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*
    /////
    /////   This determines if a sphere is inside of our frustum by it's center and radius.
    /////
    ///////////////////////////////// SPHERE IN FRUSTUM \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*
    fun sphereInFrustum(x: Float, y: Float, z: Float, radius: Float): Boolean {
        // Go through all the sides of the frustum
        for (i in 0..5) {

            // If the center of the sphere is farther away from the plane than the radius
            if (values[i][A] * x + values[i][B] * y + values[i][C] * z + values[i][D] <= -radius) {
                // The distance was greater than the radius so the sphere is outside of the frustum
                return false
            }
        }

        // The sphere was inside of the frustum!
        return true
    }

    fun cubeInFrustum(center: Vector3f, size: Float): Boolean {
        return cubeInFrustum(center.x, center.y, center.z, size)
    }

    ///////////////////////////////// CUBE IN FRUSTUM \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*
    /////
    /////   This determines if a cube is in or around our frustum by it's center and 1/2 it's length
    /////
    ///////////////////////////////// CUBE IN FRUSTUM \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*
    fun cubeInFrustum(x: Float, y: Float, z: Float, size: Float): Boolean {
        // This test is a bit more work, but not too much more complicated.
        // Basically, what is going on is, that we are given the center of the cube,
        // and half the length.  Think of it like a radius.  Then we checking each point
        // in the cube and seeing if it is inside the frustum.  If a point is found in front
        // of a side, then we skip to the next side.  If we get to a plane that does NOT have
        // a point in front of it, then it will return false.

        // *Note* - This will sometimes say that a cube is inside the frustum when it isn't.
        // This happens when all the corners of the bounding box are not behind any one plane.
        // This is rare and shouldn't effect the overall rendering speed.
        for (i in 0..5) {
            if (values[i][A] * (x - size) + values[i][B] * (y - size) + values[i][C] * (z - size) + values[i][D] > 0) continue
            if (values[i][A] * (x + size) + values[i][B] * (y - size) + values[i][C] * (z - size) + values[i][D] > 0) continue
            if (values[i][A] * (x - size) + values[i][B] * (y + size) + values[i][C] * (z - size) + values[i][D] > 0) continue
            if (values[i][A] * (x + size) + values[i][B] * (y + size) + values[i][C] * (z - size) + values[i][D] > 0) continue
            if (values[i][A] * (x - size) + values[i][B] * (y - size) + values[i][C] * (z + size) + values[i][D] > 0) continue
            if (values[i][A] * (x + size) + values[i][B] * (y - size) + values[i][C] * (z + size) + values[i][D] > 0) continue
            if (values[i][A] * (x - size) + values[i][B] * (y + size) + values[i][C] * (z + size) + values[i][D] > 0) continue
            if (values[i][A] * (x + size) + values[i][B] * (y + size) + values[i][C] * (z + size) + values[i][D] > 0) continue

            // If we get here, it isn't in the frustum
            return false
        }
        return true
    }

    fun toFloatBuffer(): FloatBuffer {
        for (i in 0..5) {
            for (z in 0..3) {
                buffer.put(values[i][z])
            }
        }
        buffer.rewind()
        return buffer
    }

    fun boxInFrustum(aabb: AABB): Boolean = cubeInFrustum(
        Vector3f(aabb.min).add(aabb.halfExtents),
        max(aabb.halfExtents.x, max(aabb.halfExtents.y, aabb.halfExtents.z))
    )

    companion object {
        private const val serialVersionUID: Long = 1
        const val RIGHT = 0 // The RIGHT side of the frustum
        const val LEFT = 1 // The LEFT      side of the frustum
        const val BOTTOM = 2 // The BOTTOM side of the frustum
        const val TOP = 3 // The TOP side of the frustum
        const val BACK = 4 // The BACK     side of the frustum
        const val FRONT = 5 // The FRONT side of the frustum

        /**
         * The X value of the plane's normal
         */
        const val A = 0

        /**
         * The Y value of the plane's normal
         */
        const val B = 1

        /**
         * The Z value of the plane's normal
         */
        const val C = 2

        /**
         * The distance the plane is from the origin
         */
        const val D = 3
    }
}