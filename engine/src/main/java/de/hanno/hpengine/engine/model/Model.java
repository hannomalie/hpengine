package de.hanno.hpengine.engine.model;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.model.material.Material;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

public interface Model {
    void setMaterial(Material material);

    List<Mesh> getMeshes();

    float getBoundingSphereRadius();

    int getTriangleCount();

    float[] getVertexBufferValuesArray();

    int[] getIndexBufferValuesArray();

    Vector4f[] getMinMax();

    IntArrayList[] getMeshIndices();

    void putToValueArrays();

    Vector3f[] getMinMax(Transform transform);
}
