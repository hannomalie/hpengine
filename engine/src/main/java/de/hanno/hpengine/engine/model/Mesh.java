package de.hanno.hpengine.engine.model;

import com.carrotsearch.hppc.FloatArrayList;
import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.model.material.Material;
import org.joml.Vector3f;

import java.util.List;

public interface Mesh {
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

}
