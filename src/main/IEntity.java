package main;

import java.nio.FloatBuffer;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;


public interface IEntity {
	public void update();
	public void draw();
	public void drawDebug();
	public void destroy();
	public void drawShadow();
	public Vector3f getPosition();
	public Quaternion getOrientation();
	public void rotate(Vector4f axisDegree);
	public void rotate(Vector3f axis, float degree);
	public void move(Vector3f amount);
	public boolean castsShadows();
	public String getName();
	public Material getMaterial();
	public void setScale(Vector3f scale);
	public void setScale(float scale);
	public void setPosition(Vector3f position);
	public void setOrientation(Quaternion orientation);
	public Matrix4f getModelMatrix();
}
