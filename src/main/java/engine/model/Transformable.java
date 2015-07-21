package engine.model;

import camera.Camera;
import engine.Transform;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

 public interface Transformable {
	Transform getTransform();
	void setTransform(Transform transform);
    default Vector3f getPosition() { return getTransform().getPosition(); };
    default Vector3f getLocalPosition() { return getTransform().getLocalPosition(); };
    default Quaternion getOrientation() { return getTransform().getOrientation(); };
    default Quaternion getLocalOrientation() { return getTransform().getLocalOrientation(); };
	default void rotate(Vector4f axisDegree) { getTransform().rotate(axisDegree); };
	default void rotate(Vector3f axis, float degree) { getTransform().rotate(axis, degree);};
	default void rotate(Vector3f axis, float radians, boolean useRadians) { getTransform().rotate(axis, (float) Math.toDegrees(radians)); };
	default void move(Vector3f amount) { getTransform().move(amount); };
	default void setScale(Vector3f scale) { getTransform().setScale(scale); };
	default void setScale(float scale) { getTransform().setScale(scale); };
    default Vector3f getScale() { return getTransform().getScale(); };
    default Vector3f getLocalScale() { return getTransform().getLocalScale(); };
	default void setPosition(Vector3f position) { getTransform().setPosition(position); };
	default void setOrientation(Quaternion orientation) { getTransform().setOrientation(orientation); };
	default void moveInWorld(Vector3f amount) { getTransform().moveInWorld(amount); };
	default void rotateWorld(Vector3f axis, float degree) { getTransform().rotateWorld(axis, degree); };
	default void rotateWorld(Vector4f axisAngle) { getTransform().rotateWorld(axisAngle); };
	default Vector3f getViewDirection() { return getTransform().getViewDirection(); };
	default Vector3f getUpDirection() { return getTransform().getUpDirection(); };
	default Vector3f getRightDirection() { return getTransform().getRightDirection(); };
	default Matrix4f getModelMatrix() { return getTransform().getTransformation(); };
	default void setModelMatrix(Matrix4f modelMatrix) {};
    default Matrix4f getViewMatrix() { return getTransform().getViewMatrix(); };
 	default void setViewMatrix(Matrix4f viewMatrix) {};

	default Vector4f[] getMinMaxWorld() {
		Vector3f position = getPosition();
		Vector4f temp = new Vector4f(position.x, position.y, position.z, 1);
		return new Vector4f[] {temp, temp};
	};

	default Vector3f getCenter() { return getPosition(); }
	default boolean isInFrustum(Camera camera) { return true; }
}
