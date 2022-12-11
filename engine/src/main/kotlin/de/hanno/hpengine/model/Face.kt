package de.hanno.hpengine.model

import org.joml.Vector3f
import java.io.Serializable

class Face {
    val vertices = intArrayOf(-1, -1, -1)
    val normalIndices = intArrayOf(-1, -1, -1)
    val textureCoordinateIndices = intArrayOf(-1, -1, -1)
    var isRemoved = false
    fun hasNormals(): Boolean {
        return normalIndices[0] != -1
    }

    fun hasTextureCoordinates(): Boolean {
        return textureCoordinateIndices[0] != -1
    }

    constructor(vertexIndices: IntArray) {
        vertices[0] = vertexIndices[0]
        vertices[1] = vertexIndices[1]
        vertices[2] = vertexIndices[2]
    }

    constructor(vertexIndices: IntArray, normalIndices: IntArray) {
        vertices[0] = vertexIndices[0]
        vertices[1] = vertexIndices[1]
        vertices[2] = vertexIndices[2]
        this.normalIndices[0] = normalIndices[0]
        this.normalIndices[1] = normalIndices[1]
        this.normalIndices[2] = normalIndices[2]
    }

    constructor(vertexIndices: IntArray, textureCoordinateIndices: IntArray, normalIndices: IntArray) {
        vertices[0] = vertexIndices[0]
        vertices[1] = vertexIndices[1]
        vertices[2] = vertexIndices[2]
        this.textureCoordinateIndices[0] = textureCoordinateIndices[0]
        this.textureCoordinateIndices[1] = textureCoordinateIndices[1]
        this.textureCoordinateIndices[2] = textureCoordinateIndices[2]
        this.normalIndices[0] = normalIndices[0]
        this.normalIndices[1] = normalIndices[1]
        this.normalIndices[2] = normalIndices[2]
    }

    fun hasVertex(vertexIndex: Int): Boolean {
        return vertexIndex == vertices[0] || vertexIndex == vertices[1] || vertexIndex == vertices[2]
    }

    fun getVertex(vertexIndex: Int): Int {
        return vertices[vertexIndex]
    }

    fun indexOf(vertexIndex: Int): Int {
        for (i in 0..2) {
            if (vertices[i] == vertexIndex) {
                return i
            }
        }
        return -1
        //        throw new IllegalArgumentException("Vertex " + vertexIndex + " is not part of triangle" + this);
    }

    companion object {
        fun calculateFaceNormal(a: Vector3f?, b: Vector3f?, c: Vector3f?): Vector3f {
            val tmpV1 = Vector3f(b).sub(a)
            val tmpV2 = Vector3f(c).sub(b)
            return Vector3f(tmpV1).cross(tmpV2).normalize()
        }
    }
}