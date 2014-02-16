package main;

import static main.log.ConsoleLogger.getLogger;

import java.nio.FloatBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import main.util.Util;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

public class Camera implements IEntity {
	private static Logger LOGGER = getLogger();
	
	FloatBuffer matrix44Buffer = BufferUtils.createFloatBuffer(16);
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

	private ForwardRenderer renderer;
	
	public Camera(ForwardRenderer renderer) {
		this(renderer, Util.createPerpective(60f, (float)ForwardRenderer.WIDTH / (float)ForwardRenderer.HEIGHT, 0.001f, 100f));
		//this(renderer, Util.createOrthogonal(-1f, 1f, -1f, 1f, -1f, 2f), Util.lookAt(new Vector3f(1,10,1), new Vector3f(0,0,0), new Vector3f(0, 1, 0)));
	}
	
	public Camera(ForwardRenderer renderer, Matrix4f projectionMatrix) {
		this(renderer, projectionMatrix, new Matrix4f());
	}
	
	public Camera(ForwardRenderer renderer, Matrix4f projectionMatrix, Matrix4f viewMatrix) {
		this.renderer = renderer;
		this.projectionMatrix = projectionMatrix;

		this.viewMatrix = viewMatrix;

		this.projectionMatrixLocation = renderer.getProjectionMatrixLocation();
		this.viewMatrixLocation = renderer.getViewMatrixLocation();
		
		position = new Vector3f(0, 0, -1);
		angle = new Vector3f(0, 0, 0);
		
	}

	public void update() {
		transform();
		updateControls();
		flipBuffers();
	}
	public void updateShadow() {
		transform();
//		updateControls();
		flipBuffersShadow();
	}
	
	public void updateControls() {

		if (Mouse.isButtonDown(0)) {
			angle.y += Mouse.getDX() * rotationSpeed;
			angle.x += -Mouse.getDY() * rotationSpeed;
			angle.z += -Mouse.getDY() * rotationSpeed;
			LOGGER.log(Level.INFO, String.format("Camera angle: %f | %f | %f", angle.x, angle.y, angle.z));
		}
		Vector3f right = new Vector3f(viewMatrix.m00, viewMatrix.m01, viewMatrix.m02);
		Vector3f up = new Vector3f(viewMatrix.m10, viewMatrix.m11, viewMatrix.m12);
		Vector3f back = new Vector3f(viewMatrix.m20, viewMatrix.m21, viewMatrix.m22);
		
		if (Keyboard.isKeyDown(Keyboard.KEY_W)) {
			position.x -= posDelta * back.x;
			position.y -= posDelta * back.y;
			position.z -= posDelta * -back.z;
			LOGGER.log(Level.INFO, String.format("Camera position: %f | %f | %f", position.x, position.y, position.z));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_A)) {
			position.x += posDelta * right.x;
			position.y += posDelta * right.y;
			position.z += posDelta * -right.z;
			LOGGER.log(Level.INFO, String.format("Camera position: %f | %f | %f", position.x, position.y, position.z));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_S)) {
			position.x += posDelta * back.x;
			position.y += posDelta * back.y;
			position.z += posDelta * -back.z;
			LOGGER.log(Level.INFO, String.format("Camera position: %f | %f | %f", position.x, position.y, position.z));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_D)) {
			position.x -= posDelta * right.x;
			position.y -= posDelta * right.y;
			position.z -= posDelta * -right.z;
			LOGGER.log(Level.INFO, String.format("Camera position: %f | %f | %f", position.x, position.y, position.z));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_Q)) {
			position.x += posDelta * up.x;
			position.y += posDelta * up.y;
			position.z += posDelta * -up.z;
			LOGGER.log(Level.INFO, String.format("Camera position: %f | %f | %f", position.x, position.y, position.z));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_E)) {
			position.x -= posDelta * up.x;
			position.y -= posDelta * up.y;
			position.z -= posDelta * -up.z;
			LOGGER.log(Level.INFO, String.format("Camera position: %f | %f | %f", position.x, position.y, position.z));
		}
	}
	

	private void transform() {
		setViewMatrix(new Matrix4f());
		Matrix4f.rotate(Util.degreesToRadians(angle.z), new Vector3f(0, 0, 1), 
				viewMatrix, viewMatrix);
		Matrix4f.rotate(Util.degreesToRadians(angle.y), new Vector3f(0, 1, 0), 
				viewMatrix, viewMatrix);
		Matrix4f.rotate(Util.degreesToRadians(angle.x), new Vector3f(1, 0, 0), 
				viewMatrix, viewMatrix);

		Matrix4f.translate(position, viewMatrix, viewMatrix);
	}

	private void flipBuffers() {
		projectionMatrix.store(matrix44Buffer); matrix44Buffer.flip();
		GL20.glUniformMatrix4(projectionMatrixLocation, false, matrix44Buffer);
		viewMatrix.store(matrix44Buffer); matrix44Buffer.flip();
		GL20.glUniformMatrix4(viewMatrixLocation, false, matrix44Buffer);
	}
	public void flipBuffersShadow() {
		projectionMatrix.store(matrix44Buffer); matrix44Buffer.flip();
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(ForwardRenderer.getShadowProgramId(),"projectionMatrix"), false, matrix44Buffer);
		viewMatrix.store(matrix44Buffer); matrix44Buffer.flip();
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(ForwardRenderer.getShadowProgramId(),"viewMatrix"), false, matrix44Buffer);
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

	@Override
	public void draw() {
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
	}

	@Override
	public void drawShadow() {
	}

}
