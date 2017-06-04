package de.hanno.hpengine.engine.model;

import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.Transform;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
public interface Transformable {
    de.hanno.hpengine.engine.Transform getTransform();
    void setTransform(Transform transform);
    default Vector3f getPosition() { return getTransform().getPosition(); };
    default Quaternionf getOrientation() { return getTransform().getOrientation(); };
    default void rotate(Vector4f axisDegree) { getTransform().rotate(axisDegree); };
    default void rotate(Vector3f axis, float degree) { getTransform().rotate(axis, degree);};
    default void rotate(Vector3f axis, float radians, boolean useRadians) { getTransform().rotate(axis, (float) Math.toDegrees(radians)); };
    default void move(Vector3f amount) { getTransform().move(amount); };
    default void setScale(Vector3f scale) { getTransform().setScale(scale); };
    default void setScale(float scale) { getTransform().setScale(scale); };
    default void setPosition(Vector3f position) { getTransform().setPosition(position); };
    default Vector3f getScale() { return getTransform().getScale(); };
    default void setOrientation(Quaternionf orientation) { getTransform().setOrientation(orientation); };
    default void moveInWorld(Vector3f amount) { getTransform().moveInWorld(amount); };
    default void rotateWorld(Vector3f axis, float degree) { getTransform().rotateWorld(axis, degree); };
    default void rotateWorld(Vector4f axisAngle) { getTransform().rotateWorld(axisAngle); };
    default void setOrientationFromAxisAngle(Vector4f axisAngle) { getTransform().setOrientationFromAxisAngle(axisAngle); };
    default Vector3f getViewDirection() { return getTransform().getViewDirection(); };
    default Vector3f getUpDirection() { return getTransform().getUpDirection(); };
    default Vector3f getRightDirection() { return getTransform().getRightDirection(); };
    default Matrix4f getModelMatrix() { return getTransform().getTransformation(); };
    default void setModelMatrix(Matrix4f modelMatrix) {};
    default Matrix4f getViewMatrix() { return getTransform().getViewMatrix(); };
    default void setViewMatrix(Matrix4f viewMatrix) {};

    default Vector3f[] getMinMaxWorld() {
        Vector3f position = getPosition();
        Vector3f temp = new Vector3f(position.x, position.y, position.z);
        return new Vector3f[] {temp, temp};
    };

    default Vector3f getCenter() { return getPosition(); }
    default boolean isInFrustum(Camera camera) { return true; }

    default Vector3f getWorldPosition() { return getTransform().getWorldPosition(); }
    default Quaternionf getWorldRotation() { return getTransform().getWorldOrientation(); }
}

