package renderer.light;

import java.io.Serializable;

import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class PointLightSerializationProxy implements Serializable {
	private static final long serialVersionUID = 1L;
	private Vector3f position;
	private Vector4f color;
	private float radius;

	public PointLightSerializationProxy(PointLight pointLight) {
		this.position = pointLight.getPosition();
		this.color = pointLight.getColor();
		this.radius = pointLight.getRadius();
	}

	public Vector3f getPosition() {
		return position;
	}

	public void setPosition(Vector3f position) {
		this.position = position;
	}

	public Vector4f getColor() {
		return color;
	}

	public void setColor(Vector4f color) {
		this.color = color;
	}

	public float getRadius() {
		return radius;
	}

	public void setRadius(float radius) {
		this.radius = radius;
	}

}
