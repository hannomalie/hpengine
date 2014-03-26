package main;

import java.nio.FloatBuffer;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;


public interface IEntity {
	default public void update() {};
	default public void draw(Program program) {};
	default public void drawDebug(Program program) {};
	public void destroy();
	default public Vector3f getPosition() { return new Vector3f(); };
	default public Quaternion getOrientation() { return new Quaternion(); };
	default public void rotate(Vector4f axisDegree) {};
	default public void rotate(Vector3f axis, float radians) {};
	default public void rotate(Vector3f axis, float degree, boolean isDegree) {};
	default public void move(Vector3f amount) {};
	public String getName();
	public Material getMaterial();
	default public void setScale(Vector3f scale) {};
	default public void setScale(float scale) {};
	default public void setPosition(Vector3f position) {};
	default public void setOrientation(Quaternion orientation) {};
	default public Matrix4f getModelMatrix() { return new Matrix4f(); };
	default public boolean isInFrustum(Camera camera) { return true; };
}
