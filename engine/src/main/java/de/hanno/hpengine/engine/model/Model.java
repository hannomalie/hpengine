package de.hanno.hpengine.engine.model;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.transform.Transform;
import de.hanno.hpengine.engine.graphics.buffer.Bufferable;
import de.hanno.hpengine.engine.model.loader.md5.AnimationController;
import de.hanno.hpengine.engine.model.material.Material;
import org.joml.Vector3f;

import java.util.List;

public interface Model<T extends Bufferable> {
    void setMaterial(Material material);

    List<Mesh<T>> getMeshes();

    float getBoundingSphereRadius();

    int getTriangleCount();

    float[] getVertexBufferValuesArray();

    int[] getIndices();

    Vector3f[] getMinMax();

    IntArrayList[] getMeshIndices();

    Vector3f[] getMinMax(Transform transform);

    List<T> getCompiledVertices();

    default boolean isStatic() {return true;}

    default float getBoundingSphereRadius(Mesh mesh, AnimationController controller) { return mesh.getBoundingSphereRadius(); }

    default Vector3f[] getMinMax(Transform transform, Mesh mesh, AnimationController animationController) {
        return mesh.getMinMax(transform);
    }

    default boolean isInvertTexCoordY() { return true; }
}
