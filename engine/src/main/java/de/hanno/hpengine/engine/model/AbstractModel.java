package de.hanno.hpengine.engine.model;

import com.carrotsearch.hppc.FloatArrayList;
import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.graphics.buffer.Bufferable;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.util.Util;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class AbstractModel<T extends Bufferable> implements Model<T> {
    protected List<Mesh> meshes = new ArrayList<>();
    protected float boundingSphereRadius;
    protected int triangleCount;
    protected IntArrayList[] meshIndices;
    private Vector3f[] minMax = { new Vector3f(), new Vector3f() };
    private Matrix4f lastUsedModelMatrix;
    private Vector3f center;

    public void setMaterial(Material material) {
        for (Mesh mesh : meshes) {
            mesh.setMaterial(material);
        }
    }

    public float getBoundingSphereRadius() {
        return boundingSphereRadius;
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

    public List<T> getCompiledVertices() {
        List<T> vertexList = new ArrayList<>();
        for(Mesh mesh : meshes) {
            vertexList.addAll((Collection<? extends T>) mesh.getCompiledVertices());
        }
        return vertexList;
    }

    public int[] getIndices() {
        IntArrayList intList = new IntArrayList();
        for(Mesh mesh : meshes) {
            intList.add(mesh.getIndexBufferValuesArray());
        }
        return intList.toArray();
    }

    public Vector3f[] getMinMax(Transform transform) {
        updateMinMax(transform);
        return minMax;
    }

    protected final void updateMinMax(Transform transform) {
        if(!(lastUsedModelMatrix == null && transform == null) || !Util.equals(lastUsedModelMatrix, transform.getTransformation())) {
            lastUsedModelMatrix = transform.getTransformation();
            actuallyUpdateMinMax(transform);
        }
    }

    private void actuallyUpdateMinMax(Transform transform) {
        for (int i = 0; i < meshes.size(); i++) {
            Mesh mesh = meshes.get(i);
            Vector3f[] meshMinMax = mesh.getMinMax(transform);
            StaticMesh.calculateMinMax(minMax[0], minMax[1], meshMinMax);
        }

        calculateCenter();
    }

    Vector3f centerTemp = new Vector3f();
    private void calculateCenter() {
        center = centerTemp.set(minMax[0]).add(new Vector3f(minMax[1]).sub(minMax[0]).mul(0.5f));
    }

    public Vector4f[] getMinMax() {
        return new Vector4f[0];
    }

    public List<Mesh> getMeshes() {
        return meshes;
    }

    public Vector3f getCenter() {
        return center;
    }

    public IntArrayList[] getMeshIndices() {
        return meshIndices;
    }
}
