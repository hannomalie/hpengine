package de.hanno.hpengine.engine.model;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.transform.AABB;
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

    AABB getMinMax();

    IntArrayList[] getMeshIndices();

    AABB getMinMax(Transform transform);

    List<T> getCompiledVertices();

    default boolean isStatic() {return true;}

    default float getBoundingSphereRadius(Mesh mesh, AnimationController controller) { return mesh.getBoundingSphereRadius(); }

    default AABB getMinMax(Transform transform, Mesh mesh, AnimationController animationController) {
        return mesh.getMinMax(transform);
    }
    default AABB getMinMax(Mesh mesh, AnimationController animationController) {
        return mesh.getMinMax();
    }

    default boolean isInvertTexCoordY() { return true; }
}
