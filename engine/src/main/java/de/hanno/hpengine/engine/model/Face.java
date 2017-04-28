package de.hanno.hpengine.engine.model;


import org.lwjgl.util.vector.Vector3f;

import java.io.Serializable;

public class Face implements Serializable {
	private final int[] vertexIndices = {-1, -1, -1};
    private final int[] normalIndices = {-1, -1, -1};
    private final int[] textureCoordinateIndices = {-1, -1, -1};
    public boolean isRemoved;

    public boolean hasNormals() {
        return normalIndices[0] != -1;
    }

    public boolean hasTextureCoordinates() {
        return textureCoordinateIndices[0] != -1;
    }

    public int[] getVertices() {
        return vertexIndices;
    }

    public int[] getTextureCoordinateIndices() {
        return textureCoordinateIndices;
    }

    public int[] getNormalIndices() {
        return normalIndices;
    }

    public Face(int[] vertexIndices) {
        this.vertexIndices[0] = vertexIndices[0];
        this.vertexIndices[1] = vertexIndices[1];
        this.vertexIndices[2] = vertexIndices[2];
    }

    public Face(int[] vertexIndices, int[] normalIndices) {
        this.vertexIndices[0] = vertexIndices[0];
        this.vertexIndices[1] = vertexIndices[1];
        this.vertexIndices[2] = vertexIndices[2];
        this.normalIndices[0] = normalIndices[0];
        this.normalIndices[1] = normalIndices[1];
        this.normalIndices[2] = normalIndices[2];
    }

    public Face(int[] vertexIndices, int[] textureCoordinateIndices, int[] normalIndices) {
        this.vertexIndices[0] = vertexIndices[0];
        this.vertexIndices[1] = vertexIndices[1];
        this.vertexIndices[2] = vertexIndices[2];
        this.textureCoordinateIndices[0] = textureCoordinateIndices[0];
        this.textureCoordinateIndices[1] = textureCoordinateIndices[1];
        this.textureCoordinateIndices[2] = textureCoordinateIndices[2];
        this.normalIndices[0] = normalIndices[0];
        this.normalIndices[1] = normalIndices[1];
        this.normalIndices[2] = normalIndices[2];
    }

    public boolean hasVertex(int vertexIndex) {
        return (vertexIndex == vertexIndices[0] || vertexIndex == vertexIndices[1] || vertexIndex == vertexIndices[2]);
    }

    public int getVertex(int vertexIndex) {
        return vertexIndices[vertexIndex];
    }

    public static Vector3f calculateFaceNormal(Vector3f a, Vector3f b, Vector3f c) {
        Vector3f tmpV1 = Vector3f.sub(b, a, null);
        Vector3f tmpV2 = Vector3f.sub(c, b, null);

        return Vector3f.cross(tmpV1, tmpV2, null).normalise(null);
    }

    public int indexOf(int vertexIndex) {
        for (int i = 0; i < 3; i++) {
            if (vertexIndices[i] == vertexIndex) {
                return i;
            }
        }
        return -1;
//        throw new IllegalArgumentException("Vertex " + vertexIndex + " is not part of triangle" + this);
    }
}
