package de.hanno.hpengine.engine.model;

import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.Transform;
import org.joml.*;

import java.lang.Math;

public interface Transformable {
    de.hanno.hpengine.engine.Transform getTransform();
    void setTransform(Transform transform);
    default Vector3f getPosition() { return getTransform().getPosition(); };
    default Quaternionf getOrientation() { return getTransform().getOrientation(); };
    default void rotate(Vector4f axisDegree) { getTransform().rotate(new AxisAngle4f(axisDegree.x, axisDegree.y, axisDegree.z, (float) Math.toRadians(axisDegree.w))); };
    default void rotate(Vector3f axis, float degree) { getTransform().rotate(new AxisAngle4f(axis.x, axis.y, axis.z, (float) Math.toRadians(degree)));};
    default void rotate(Vector3f axis, float radians, boolean useRadians) { getTransform().rotate(new AxisAngle4f(radians, axis)); };
    default void move(Vector3f amount) {
        getTransform().translateLocal(amount);
    };
    default void setScale(float scale) { getTransform().scale(scale); };
    default void setPosition(Vector3f position) {
        getTransform().setTranslation(position);
    };
    default Vector3f getScale() { return getTransform().getScale(); };
    default Vector3f getViewDirection() { return getTransform().getViewDirection(); };
    default Vector3f getUpDirection() { return getTransform().getUpDirection(); };
    default Vector3f getRightDirection() { return getTransform().getRightDirection(); };
    default Matrix4f getModelMatrix() { return getTransform().getTransformation(); };
    default void setModelMatrix(Matrix4f modelMatrix) {};

    default Vector3f[] getMinMaxWorld() {
        Vector3f position = getPosition();
        Vector3f temp = new Vector3f(position.x, position.y, position.z);
        return new Vector3f[] {temp, temp};
    };

    default Vector3f getCenter() { return getPosition(); }
    default boolean isInFrustum(Camera camera) { return true; }

}

