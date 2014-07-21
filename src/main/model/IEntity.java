package main.model;

import java.util.HashMap;

import main.Transform;
import main.camera.Camera;
import main.component.IGameComponent;
import main.component.IGameComponent.ComponentIdentifier;
import main.renderer.Renderer;
import main.renderer.material.Material;
import main.shader.Program;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;


public interface IEntity {
	default public void update(float seconds) {};
	
	default public void draw(Renderer renderer, Camera camera) {};
	default public void drawDebug(Program program) {};

	public Transform getTransform();
	public void setTransform(Transform transform);
	default public Vector3f getPosition() { return getTransform().getPosition(); };
	default public Quaternion getOrientation() { return getTransform().getOrientation(); };
	default public void rotate(Vector4f axisDegree) { getTransform().rotate(axisDegree); };
	default public void rotate(Vector3f axis, float radians) { getTransform().rotate(axis, radians);};
	default public void rotate(Vector3f axis, float degree, boolean isDegree) { getTransform().rotate(axis, degree); };
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
	
	public String getName();
	default public void setName(String string) {}
	
	public Material getMaterial();
	default void setMaterial(String materialName) {};

	default public Vector3f getCenter() { return getPosition(); }
	default public boolean isInFrustum(Camera camera) { return true; }
	default public boolean isVisible() { return true; }
	default public void setVisible(boolean visible) {};
	default public Vector4f[] getMinMaxWorld() {
		Vector3f position = getPosition();
		Vector4f temp = new Vector4f(position.x, position.y, position.z, 1);
		return new Vector4f[] {temp, temp};
	};
	
	public boolean isSelected();
	public void setSelected(boolean selected);
	
	public default VertexBuffer getVertexBuffer() { return null; }
	
	public default void destroy() {}

	public default HashMap<ComponentIdentifier, IGameComponent> getComponents() { return new HashMap<>(); }
}
