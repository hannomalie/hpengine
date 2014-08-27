package main.model;

import main.Transform;
import main.camera.Camera;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public interface ITransformable {
	public Transform getTransform();
	public void setTransform(Transform transform);
	default public Vector3f getPosition() { return getTransform().getPosition(); };
	default public Quaternion getOrientation() { return getTransform().getOrientation(); };
	default public void rotate(Vector4f axisDegree) { getTransform().rotate(axisDegree); };
	default public void rotate(Vector3f axis, float degree) { getTransform().rotate(axis, degree);};
	default public void rotate(Vector3f axis, float radians, boolean useRadians) { getTransform().rotate(axis, (float) Math.toDegrees(radians)); };
	default public void move(Vector3f amount) { getTransform().move(amount); };
	default public void setScale(Vector3f scale) { getTransform().setScale(scale); };
	default public void setScale(float scale) { getTransform().setScale(scale); };
	default public Vector3f getScale() { return getTransform().getScale(); };
	default public void setPosition(Vector3f position) { getTransform().setPosition(position); };
	default public void setOrientation(Quaternion orientation) { getTransform().setOrientation(orientation); };
	default void moveInWorld(Vector3f amount) { getTransform().moveInWorld(amount); };
	default void rotateWorld(Vector3f axis, float degree) { getTransform().rotateWorld(axis, degree); };
	default void rotateWorld(Vector4f axisAngle) { getTransform().rotateWorld(axisAngle); };
	default Vector3f getViewDirection() { return getTransform().getViewDirection(); };
	default Vector3f getUpDirection() { return getTransform().getUpDirection(); };
	default Vector3f getRightDirection() { return getTransform().getRightDirection(); };
	default public Matrix4f getModelMatrix() { return Matrix4f.setIdentity(null); };
	default void setModelMatrix(Matrix4f modelMatrix) {};
	
	default public Vector4f[] getMinMaxWorld() {
		Vector3f position = getPosition();
		Vector4f temp = new Vector4f(position.x, position.y, position.z, 1);
		return new Vector4f[] {temp, temp};
	};
	
	default public Vector3f getCenter() { return getPosition(); }
	default public boolean isInFrustum(Camera camera) { return true; }
}
