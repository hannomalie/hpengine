package de.hanno.hpengine.engine.model;

import com.carrotsearch.hppc.FloatArrayList;
import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.util.Util;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import java.util.ArrayList;
import java.util.List;

public class Model {
    private List<Mesh> meshes = new ArrayList();

    private ArrayList<Vector3f> vertices = new ArrayList<>();
    private ArrayList<Vector2f> texCoords = new ArrayList<>();
    private ArrayList<Vector3f> normals = new ArrayList<>();
    private float boundingSphereRadius;
    private int triangleCount;
    private Vector3f[] minMax = { new Vector3f(), new Vector3f() };
    private IntArrayList[] meshIndices;
    private Matrix4f lastUsedModelMatrix;

    public Model() {

    }

    public void addVertex(Vector3f vertex) {
        vertices.add(vertex);
    }

    public void addTexCoords(Vector2f texCoords) {
        this.texCoords.add(texCoords);
    }

    public void addNormal(Vector3f normal) {
        normals.add(normal);
    }

    public ArrayList<Vector3f> getVertices() {
        return vertices;
    }

    public ArrayList<Vector2f> getTexCoords() {
        return texCoords;
    }

    public ArrayList<Vector3f> getNormals() {
        return normals;
    }

    public void init() {
        meshIndices = new IntArrayList[meshes.size()];
        triangleCount = 0;
        for(int i = 0; i < meshes.size(); i++) {
            Mesh mesh = meshes.get(i);
            mesh.init();
            meshIndices[i] = new IntArrayList();
            meshIndices[i].add(mesh.getIndexBufferValuesArray());
            triangleCount += mesh.getTriangleCount();
        }
    }

    public void setMaterial(Material material) {
        for (Mesh mesh : meshes) {
            mesh.setMaterial(material);
        }
    }

    public float getBoundingSphereRadius() {
        return boundingSphereRadius;
    }

    public void setBoundingSphereRadius(float boundingSphereRadius) {
        this.boundingSphereRadius = boundingSphereRadius;
    }

    public int getTriangleCount() {
        return triangleCount;
    }

    public float[] getVertexBufferValuesArray() {
        FloatArrayList floatList = new FloatArrayList();
        for(Mesh mesh : meshes) {
            floatList.add(mesh.getVertexBufferValuesArray());
        }
        return floatList.toArray();
    }

    public int[] getIndexBufferValuesArray() {
        IntArrayList intList = new IntArrayList();
        for(Mesh mesh : meshes) {
            intList.add(mesh.getIndexBufferValuesArray());
        }
        return intList.toArray();
    }

    public Vector3f[] getMinMax(Matrix4f modelMatrix) {
        if(!(lastUsedModelMatrix == null && modelMatrix == null) || !Util.equals(lastUsedModelMatrix, modelMatrix))

        lastUsedModelMatrix = modelMatrix;

        for(int i = 0; i < meshes.size(); i++) {
            Mesh mesh = meshes.get(i);
            Vector3f[] meshMinMax = mesh.getMinMax(modelMatrix);
            Mesh.calculateMinMax(minMax[0], minMax[1], meshMinMax);
        }
        return minMax;
    }

    public void putToValueArrays() {
        for(Mesh mesh : meshes) {
            mesh.putToValueArrays();
        }
    }

    public Vector4f[] getMinMax() {
        return new Vector4f[0];
    }

    public List<Mesh> getMeshes() {
        return meshes;
    }

    public Mesh getMesh(int i) {
        return meshes.get(i);
    }

    public void addMesh(Mesh mesh) {
        meshes.add(mesh);
    }

    public IntArrayList[] getMeshIndices() {
        return meshIndices;
    }
}