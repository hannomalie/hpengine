package main.scene;

import java.io.Serializable;

import main.scene.EnvironmentProbe.Update;

import org.lwjgl.util.vector.Vector3f;

public class ProbeData implements Serializable {
	private static final long serialVersionUID = 1L;
	public ProbeData(){	}
	public ProbeData(Vector3f center, Vector3f size, Update update) {
		this.center = center;
		this.size = size;
		this.update = update;
	}
	private Vector3f center;
	private Vector3f size;
	private Update update;
	private float weight = 1;
	
	public Vector3f getCenter() {
		return center;
	}
	public void setCenter(Vector3f center) {
		this.center = center;
	}
	public Vector3f getSize() {
		return size;
	}
	public void setSize(Vector3f size) {
		this.size = size;
	}
	public Update getUpdate() {
		return update;
	}
	public void setUpdate(Update update) {
		this.update = update;
	}
	public float getWeight() {
		return weight;
	}
	public void setWeight(float weight) {
		this.weight = weight;
	}
	
	@Override
	public boolean equals(Object other) {
		if(!(other instanceof ProbeData)) { return false; }
		
		ProbeData b = (ProbeData) other;
		
		return (this.center == b.center && this.size == b.size && this.update == b.update);
	}
}