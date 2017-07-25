package de.hanno.hpengine.engine.model;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.scene.Vertex;
import org.joml.Vector3f;

import java.util.List;

public interface Mesh<Vertex> {
    int MAX_WEIGHTS = 4;

    float[] getVertexBufferValuesArray();

    int[] getIndexBufferValuesArray();

    int getTriangleCount();

    Material getMaterial();

    void init();

    void setMaterial(Material material);

    void putToValueArrays();

    Vector3f[] getMinMax(Transform transform);

    Vector3f getCenter(Transform transform);

    float getBoundingSphereRadius();

    String getName();

    List<StaticMesh.CompiledFace> getFaces();

    IntArrayList getIndexBufferValues();

    void setName(String name);

    List<Vertex> getCompiledVertices();
}
