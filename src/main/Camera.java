package main;

import java.awt.RenderingHints.Key;
import java.nio.FloatBuffer;

import main.util.Util;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

public class Camera {
	private int projectionMatrixLocation = 0;
	private int viewMatrixLocation = 0;
	
	private float rotationDelta = 15f;
	private float scaleDelta = 0.1f;
	private float posDelta = 0.01f;
	
	Vector3f scaleAddResolution = new Vector3f(scaleDelta, scaleDelta, scaleDelta);
	Vector3f scaleMinusResolution = new Vector3f(-scaleDelta, -scaleDelta, -scaleDelta);
	
	private Vector3f position = null;
	private Vector3f angle = null;
	
	private Matrix4f projectionMatrix = null;
	private Matrix4f viewMatrix = null;
	private float rotationSpeed = 0.12f;
	
	public Camera() {
		projectionMatrix = new Matrix4f();
		float fieldOfView = 60f;
		float aspectRatio = (float)TheQuadExampleMoving.WIDTH / (float)TheQuadExampleMoving.HEIGHT;
		float near_plane = 0.001f;
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
	
	public void updateControls() {

		if (Mouse.isButtonDown(0)) {
			angle.y += Mouse.getDX() * rotationSpeed;
			angle.x += -Mouse.getDY() * rotationSpeed;
		}
		Vector3f right = new Vector3f(viewMatrix.m00, viewMatrix.m01, viewMatrix.m02);
		Vector3f up = new Vector3f(viewMatrix.m10, viewMatrix.m11, viewMatrix.m12);
		Vector3f back = new Vector3f(viewMatrix.m20, viewMatrix.m21, viewMatrix.m22);
		
		if (Keyboard.isKeyDown(Keyboard.KEY_W)) {
			position.x -= posDelta * back.x;
			position.y -= posDelta * back.y;
			position.z -= posDelta * -back.z;
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_A)) {
			position.x += posDelta * right.x;
			position.y += posDelta * right.y;
			position.z += posDelta * -right.z;
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_S)) {
			position.x += posDelta * back.x;
			position.y += posDelta * back.y;
			position.z += posDelta * -back.z;
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_D)) {
			position.x -= posDelta * right.x;
			position.y -= posDelta * right.y;
			position.z -= posDelta * -right.z;
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_Q)) {
			position.x += posDelta * up.x;
			position.y += posDelta * up.y;
			position.z += posDelta * -up.z;
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_E)) {
			position.x -= posDelta * up.x;
			position.y -= posDelta * up.y;
			position.z -= posDelta * -up.z;
		}
	}
	

	public void transform() {
		setViewMatrix(new Matrix4f());
		Matrix4f.rotate(Util.degreesToRadians(angle.z), new Vector3f(0, 0, 1), 
				viewMatrix, viewMatrix);
		Matrix4f.rotate(Util.degreesToRadians(angle.y), new Vector3f(0, 1, 0), 
				viewMatrix, viewMatrix);
		Matrix4f.rotate(Util.degreesToRadians(angle.x), new Vector3f(1, 0, 0), 
				viewMatrix, viewMatrix);

		Matrix4f.translate(position, viewMatrix, viewMatrix);
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
