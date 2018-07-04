package de.hanno.hpengine.engine.model;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.model.material.MaterialManager;
import de.hanno.hpengine.engine.transform.AABB;
import de.hanno.hpengine.engine.transform.Transform;
import de.hanno.hpengine.engine.graphics.buffer.Bufferable;
import de.hanno.hpengine.engine.model.material.SimpleMaterial;
import org.joml.Vector3f;

import java.util.List;

public interface Mesh<T extends Bufferable> {

    Transform<Transform> IDENTITY = new Transform<>();
    int MAX_WEIGHTS = 4;

    float[] getVertexBufferValuesArray();

    int[] getIndexBufferValuesArray();

    int getTriangleCount();

    SimpleMaterial getMaterial();

    void init(MaterialManager materialManager);

    void setMaterial(SimpleMaterial material);

    void putToValueArrays();

    AABB getMinMax(Transform transform);

    Vector3f getCenter(Entity transform);

    float getBoundingSphereRadius();

    String getName();

    List<StaticMesh.CompiledFace> getFaces();

    IntArrayList getIndexBufferValues();

    void setName(String name);

    List<T> getCompiledVertices();

    default AABB getMinMax() { return getMinMax(IDENTITY); }
}
