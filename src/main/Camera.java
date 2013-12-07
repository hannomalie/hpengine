package main;

import java.nio.FloatBuffer;

import main.util.Util;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

public class Camera {
	private int projectionMatrixLocation = 0;
	private int viewMatrixLocation = 0;
	
	private float rotationDelta = 15f;
	private float scaleDelta = 0.1f;
	private float posDelta = 0.1f;
	
	Vector3f scaleAddResolution = new Vector3f(scaleDelta, scaleDelta, scaleDelta);
	Vector3f scaleMinusResolution = new Vector3f(-scaleDelta, -scaleDelta, -scaleDelta);
	
	private Vector3f position = null;
	private Vector3f angle = null;
	
	private Matrix4f projectionMatrix = null;
	private Matrix4f viewMatrix = null;
	
	public Camera() {
		projectionMatrix = new Matrix4f();
		float fieldOfView = 60f;
		float aspectRatio = (float)TheQuadExampleMoving.WIDTH / (float)TheQuadExampleMoving.HEIGHT;
		float near_plane = 0.1f;
		float far_plane = 100f;
		
		float y_scale = Util.coTangent(Util.degreesToRadians(fieldOfView / 2f));
		float x_scale = y_scale / aspectRatio;
		float frustum_length = far_plane - near_plane;
		
		projectionMatrix.m00 = x_scale;
		projectionMatrix.m11 = y_scale;
		projectionMatrix.m22 = -((far_plane + near_plane) / frustum_length);
		projectionMatrix.m23 = -1;
		projectionMatrix.m32 = -((2 * near_plane * far_plane) / frustum_length);
        projectionMatrix.m33 = 0;
        
		viewMatrix = new Matrix4f();
		
		position = new Vector3f(0, 0, -1);
		angle = new Vector3f(0, 0, 0);
	}
	
	public void update() {
		
	}
	
	public void updateControls(int eventKey) {
		
		// Change model scale, rotation and translation values
		switch (eventKey) {
		// Move
		case Keyboard.KEY_W:
			position.y -= posDelta;
			break;
		case Keyboard.KEY_S:
			position.y += posDelta;
			break;
		case Keyboard.KEY_A:
			position.x += posDelta;
			break;
		case Keyboard.KEY_D:
			position.x -= posDelta;
			break;
		// Rotation
		case Keyboard.KEY_LEFT:
			angle.z += rotationDelta;
			break;
		case Keyboard.KEY_RIGHT:
			angle.z -= rotationDelta;
			break;
		case Keyboard.KEY_E:
			position.z += posDelta;
			break;
		case Keyboard.KEY_Q:
			position.z -= posDelta;
			break;
		}
	}
	

	public void transform(Matrix4f viewMatrix) {

		Matrix4f.translate(position, viewMatrix, viewMatrix);
		Matrix4f.rotate(Util.degreesToRadians(angle.z), new Vector3f(0, 0, 1), 
				viewMatrix, viewMatrix);
		Matrix4f.rotate(Util.degreesToRadians(angle.y), new Vector3f(0, 1, 0), 
				viewMatrix, viewMatrix);
		Matrix4f.rotate(Util.degreesToRadians(angle.x), new Vector3f(1, 0, 0), 
				viewMatrix, viewMatrix);
	}
	
	public void flipBuffers(FloatBuffer matrix44Buffer) {
		projectionMatrix.store(matrix44Buffer); matrix44Buffer.flip();
		GL20.glUniformMatrix4(projectionMatrixLocation, false, matrix44Buffer);
		viewMatrix.store(matrix44Buffer); matrix44Buffer.flip();
		GL20.glUniformMatrix4(viewMatrixLocation, false, matrix44Buffer);
	}

	public Matrix4f getProjectionMatrix() {
		return projectionMatrix;
	}

	public void setProjectionMatrix(Matrix4f projectionMatrix) {
		this.projectionMatrix = projectionMatrix;
	}

	public Matrix4f getViewMatrix() {
		return viewMatrix;
	}

	public void setViewMatrix(Matrix4f viewMatrix) {
		this.viewMatrix = viewMatrix;
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
	
	public int getProjectionMatrixLocation() {
		return projectionMatrixLocation;
	}

	public void setProjectionMatrixLocation(int projectionMatrixLocation) {
		this.projectionMatrixLocation = projectionMatrixLocation;
	}

	public int getViewMatrixLocation() {
		return viewMatrixLocation;
	}

	public void setViewMatrixLocation(int viewMatrixLocation) {
		this.viewMatrixLocation = viewMatrixLocation;
	}

}
