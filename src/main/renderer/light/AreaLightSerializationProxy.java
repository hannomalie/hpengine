package main.renderer.light;

import java.io.Serializable;

import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;

public class AreaLightSerializationProxy implements Serializable {
	private static final long serialVersionUID = 1L;
	private Vector3f position;
	private Vector3f color;
	private float radius;
	private float width;
	private float height;
	private Quaternion orientation;

	public AreaLightSerializationProxy(AreaLight areaLight) {
		this.position = areaLight.getPosition();
		this.color = areaLight.getColor();
		this.width = areaLight.getWidth();
		this.height = areaLight.getHeight();
		this.orientation = areaLight.getOrientation();
	}

	public Vector3f getPosition() {
		return position;
	}

	public void setPosition(Vector3f position) {
		this.position = position;
	}

	public Vector3f getColor() {
		return color;
	}

	public void setColor(Vector3f color) {
		this.color = color;
	}

	public float getRadius() {
		return radius;
	}

	public void setRadius(float radius) {
		this.radius = radius;
	}

	public float getWidth() {
		return width;
	}

	public void setWidth(float width) {
		this.width = width;
	}

	public float getHeight() {
		return height;
	}

	public void setHeight(float height) {
		this.height = height;
	}

	public Quaternion getOrientation() {
		return orientation;
	}

	public void setOrientation(Quaternion orientation) {
		this.orientation = orientation;
	}

}
