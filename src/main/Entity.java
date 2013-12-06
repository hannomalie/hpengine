package main;

import java.nio.FloatBuffer;

import main.util.Util;

import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

public class Entity {
	private Matrix4f modelMatrix = null;
	private int modelMatrixLocation = 0;
	private Vector3f position = null;
	private Vector3f angle = null;
	private Vector3f scale = null;
	
	public Entity() {
		modelMatrix = new Matrix4f();

		position = new Vector3f(0, 0, 0);
		angle = new Vector3f(0, 0, 0);
		scale = new Vector3f(1, 1, 1);
	}

	public void transform(Matrix4f viewMatrix) {
		Matrix4f.scale(scale, modelMatrix, modelMatrix);
		Matrix4f.translate(position, modelMatrix, modelMatrix);
		Matrix4f.rotate(Util.degreesToRadians(angle.z), new Vector3f(0, 0, 1), 
				viewMatrix, viewMatrix);
		Matrix4f.rotate(Util.degreesToRadians(angle.y), new Vector3f(0, 1, 0), 
				viewMatrix, viewMatrix);
		Matrix4f.rotate(Util.degreesToRadians(angle.x), new Vector3f(1, 0, 0), 
				viewMatrix, viewMatrix);
	}

	public void flipBuffers(FloatBuffer matrix44Buffer) {
		modelMatrix.store(matrix44Buffer); matrix44Buffer.flip();
		GL20.glUniformMatrix4(modelMatrixLocation, false, matrix44Buffer);
	}
	
	public Matrix4f getModelMatrix() {
		return modelMatrix;
	}

	public void setModelMatrix(Matrix4f modelMatrix) {
		this.modelMatrix = modelMatrix;
	}

	public Vector3f getPosition() {
		return position;
	}

	public void setPosition(Vector3f position) {
		this.position = position;
	}

	public Vector3f getAngle() {
		return angle;
	}

	public void setAngle(Vector3f angle) {
		this.angle = angle;
	}

	public Vector3f getScale() {
		return scale;
	}

	public void setScale(Vector3f scale) {
		this.scale = scale;
	}


	public int getModelMatrixLocation() {
		return modelMatrixLocation;
	}

	public void setModelMatrixLocation(int modelMatrixLocation) {
		this.modelMatrixLocation = modelMatrixLocation;
	}

}
