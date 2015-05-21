package main.renderer.light;

import java.io.Serializable;

import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class DirectionalLightSerializationProxy implements Serializable {
	private static final long serialVersionUID = 1L;
	private Vector3f position = new Vector3f();
	private Vector3f color = new Vector3f();
	private Quaternion orientation = new Quaternion();

	public DirectionalLightSerializationProxy(DirectionalLight directionalLight) {
		this.orientation = directionalLight.getOrientation();
		this.position = directionalLight.getPosition();
		this.color = directionalLight.getColor();
	}
	
	public DirectionalLightSerializationProxy() {}

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

	public Quaternion getOrientation() {
		return orientation;
	}

	public void setOrientation(Quaternion orientation) {
		this.orientation = orientation;
	}

}
