package de.hanno.hpengine.engine.model;

import com.carrotsearch.hppc.FloatArrayList;
import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.transform.SimpleSpatial;
import de.hanno.hpengine.engine.transform.Transform;
import de.hanno.hpengine.engine.graphics.buffer.Bufferable;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.transform.Spatial;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class AbstractModel<T extends Bufferable> extends SimpleSpatial implements Model<T>, Spatial {
    protected final List<Mesh<T>> meshes;
    protected int triangleCount;
    protected IntArrayList[] meshIndices;

    public AbstractModel(List<Mesh<T>> meshes) {
        this.meshes = meshes;
    }

    protected void init() {
        for (int i = 0; i < meshes.size(); i++) {
            Mesh mesh = meshes.get(i);
            Vector3f[] meshMinMax = mesh.getMinMax();
            StaticMesh.calculateMinMax(getMinMax()[0], getMinMax()[1], meshMinMax);
            StaticMesh.calculateMinMax(meshMinMax[0], meshMinMax[1], getMinMaxProperty());
        }
    }

    @Override
    public Vector3f[] getMinMax() {
        Vector3f[] temp = super.getMinMax();
        super.getCenterWorld().add(new Vector3f(-getBoundingSphereRadius()), temp[0]);
        super.getCenterWorld().add(new Vector3f(getBoundingSphereRadius()), temp[1]);
        return temp;
    }

    public void setMaterial(Material material) {
        for (Mesh mesh : meshes) {
            mesh.setMaterial(material);
        }
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

    @Override
    public Vector3f[] getMinMax(Transform transform) {
        return super.getMinMaxWorld(transform);
    }

    public List<Mesh<T>> getMeshes() {
        return meshes;
    }

    public IntArrayList[] getMeshIndices() {
        return meshIndices;
    }

}
