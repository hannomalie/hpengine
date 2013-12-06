package main;

import org.lwjgl.util.vector.Matrix4f;

public class Camera {
	
	private Matrix4f projectionMatrix = null;

	public Matrix4f getProjectionMatrix() {
		return projectionMatrix;
	}

	public void setProjectionMatrix(Matrix4f projectionMatrix) {
		this.projectionMatrix = projectionMatrix;
	}

}
