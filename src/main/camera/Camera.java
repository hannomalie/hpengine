package main.camera;

import static main.log.ConsoleLogger.getLogger;

import java.nio.FloatBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import main.model.IEntity;
import main.renderer.Renderer;
import main.renderer.material.Material;
import main.util.Util;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class Camera implements IEntity {
	private static Logger LOGGER = getLogger();

	FloatBuffer projectionMatrixBuffer = BufferUtils.createFloatBuffer(16);
	FloatBuffer viewMatrixBuffer = BufferUtils.createFloatBuffer(16);
	private int projectionMatrixLocation = 0;
	private int viewMatrixLocation = 0;
	
	private float rotationDelta = 15f;
	private float scaleDelta = 0.1f;
	private float posDelta = 12f;
	
	Vector3f scaleAddResolution = new Vector3f(scaleDelta, scaleDelta, scaleDelta);
	Vector3f scaleMinusResolution = new Vector3f(-scaleDelta, -scaleDelta, -scaleDelta);
	
	private Vector3f position = null;
	private Quaternion orientation = new Quaternion();
	
	private Matrix4f projectionMatrix = null;
	private Matrix4f viewMatrix = null;
	private float rotationSpeed = 0.02f;

	private Renderer renderer;

	private Vector3f right = new Vector3f(1, 0, 0);
	private Vector3f up = new Vector3f(0, 1, 0);
	private Vector3f back = new Vector3f(0, 0, 1);

	private Frustum frustum;

	private boolean selected;
	
	public Camera(Renderer renderer) {
		this(renderer, Util.createPerpective(60f, (float)Renderer.WIDTH / (float)Renderer.HEIGHT, 0.1f, 1000f));
		//this(renderer, Util.createOrthogonal(-1f, 1f, -1f, 1f, -1f, 2f), Util.lookAt(new Vector3f(1,10,1), new Vector3f(0,0,0), new Vector3f(0, 1, 0)));
	}
	
	public Camera(Renderer renderer, Matrix4f projectionMatrix) {
		this(renderer, projectionMatrix, new Matrix4f());
	}
	
	public Camera(Renderer renderer, Matrix4f projectionMatrix, Matrix4f viewMatrix) {
		this.renderer = renderer;
		this.projectionMatrix = projectionMatrix;

		this.viewMatrix = viewMatrix;

		position = new Vector3f(0, 0, -1);
		frustum = new Frustum(this);
	}

	public void update(float seconds) {
		transform();
		updateControls(seconds);
		storeMatrices();
	}
	public void updateShadow() {
//		transform();

		storeMatrices();
	}
	
	private void storeMatrices() {
		projectionMatrix.store(projectionMatrixBuffer); projectionMatrixBuffer.flip();
		viewMatrix.store(viewMatrixBuffer); viewMatrixBuffer.flip();
	}
	
	public void updateControls(float seconds) {

		if (Mouse.isButtonDown(0)) {
			rotate(up, Mouse.getDX() * rotationSpeed);
//			rotate(right, Mouse.getDY() * rotationSpeed/2);
			LOGGER.log(Level.INFO, String.format("Camera angle: %f | %f | %f | %f", orientation.x, orientation.y, orientation.z, orientation.w));
		}
		if (Mouse.isButtonDown(1)) {
			rotate(right, Mouse.getDY() * rotationSpeed/2);
			LOGGER.log(Level.INFO, String.format("Camera angle: %f | %f | %f | %f", orientation.x, orientation.y, orientation.z, orientation.w));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_W)) {
			position.x -= posDelta * seconds * back.x;
			position.y -= posDelta * seconds * back.y;
			position.z -= posDelta * seconds * -back.z;
			LOGGER.log(Level.INFO, String.format("Camera position: %f | %f | %f", position.x, position.y, position.z));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_A)) {
			position.x += posDelta * seconds * right.x;
			position.y += posDelta * seconds * right.y;
			position.z += posDelta * seconds * -right.z;
			LOGGER.log(Level.INFO, String.format("Camera position: %f | %f | %f", position.x, position.y, position.z));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_S)) {
			position.x += posDelta * seconds * back.x;
			position.y += posDelta * seconds * back.y;
			position.z += posDelta * seconds * -back.z;
			LOGGER.log(Level.INFO, String.format("Camera position: %f | %f | %f", position.x, position.y, position.z));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_D)) {
			position.x -= posDelta * seconds * right.x;
			position.y -= posDelta * seconds * right.y;
			position.z -= posDelta * seconds * -right.z;
			LOGGER.log(Level.INFO, String.format("Camera position: %f | %f | %f", position.x, position.y, position.z));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_Q)) {
			position.x += posDelta * seconds * up.x;
			position.y += posDelta * seconds * up.y;
			position.z += posDelta * seconds * -up.z;
			LOGGER.log(Level.INFO, String.format("Camera position: %f | %f | %f", position.x, position.y, position.z));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_E)) {
			position.x -= posDelta * seconds * up.x;
			position.y -= posDelta * seconds * up.y;
			position.z -= posDelta * seconds * -up.z;
			LOGGER.log(Level.INFO, String.format("Camera position: %f | %f | %f", position.x, position.y, position.z));
		}
	}
	

	private void transform() {
		setViewMatrix(calculateCurrentViewMatrix());

//		System.out.println(viewMatrix);
//		System.out.println(getPosition());
//		System.out.println(Matrix4f.transform((Matrix4f) viewMatrix.invert(), new Vector4f(position.x, position.y, position.z, 1), null));

//		Vector4f temp = Matrix4f.transform(rotation, new Vector4f(1f,0f,0f,0f), null);
//		right = new Vector3f(temp.x, temp.y, temp.z);
		right = (Vector3f) new Vector3f(viewMatrix.m00, viewMatrix.m01, viewMatrix.m02).normalise();
		up = (Vector3f) new Vector3f(viewMatrix.m10, viewMatrix.m11, viewMatrix.m12).normalise();
//		temp = Matrix4f.transform(rotation, new Vector4f(0f,1f,0f,0f), null);
//		up = new Vector3f(temp.x, temp.y, temp.z);
		back = (Vector3f) new Vector3f(viewMatrix.m20, viewMatrix.m21, viewMatrix.m22).normalise();
//		temp = Matrix4f.transform(rotation, new Vector4f(0f,0f,-1f,0f), null);
//		back = new Vector3f(temp.x, temp.y, temp.z);

		frustum.calculate(this);
	}
	
	private Matrix4f calculateCurrentViewMatrix() {
		viewMatrix = new Matrix4f();
		Matrix4f.translate(position, viewMatrix, viewMatrix);
		Matrix4f.mul(Util.toMatrix(orientation), viewMatrix, viewMatrix);
		
		return viewMatrix;
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

	@Override
	public Vector3f getPosition() {
		return position;
	}

	public void setPosition(Vector3f position) {
		this.position = position;
		transform();
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
	public void destroy() {
	}

	@Override
	public void move(Vector3f amount) {
		amount.z = -amount.z;
		setPosition(Vector3f.add(getPosition(), amount, null));
		transform();
	}

	@Override
	public String getName() {
		return "Camera";
	}

	@Override
	public Material getMaterial() {
		return null;
	}

	@Override
	public Quaternion getOrientation() {
		return orientation;
	}

	@Override
	public void rotate(Vector4f axisAngle) {
		Quaternion rot = new Quaternion();
		rot.setFromAxisAngle(axisAngle);
		Quaternion.mul(orientation, rot, orientation);
		orientation.normalise(orientation);
		transform();
	}

	public float getRotationSpeed() {
		return rotationSpeed;
	}

	public Vector3f getRight() {
		return right;
	}
	public Vector3f getUp() {
		return up;
	}
	public Vector3f getBack() {
		return back;
	}

	@Override
	public void rotate(Vector3f axis, float radians) {
		rotate(new Vector4f(axis.x, axis.y, axis.z, radians));
	}
	@Override
	public void rotate(Vector3f axis, float degree, boolean isDegree) {
		float radians = (float) Math.toRadians(degree);
		axis.normalise(axis);
		rotate(new Vector4f(axis.x, axis.y, axis.z, radians));
	}

	public void setOrientation(Quaternion orientation) {
		this.orientation = orientation;
		transform();
	}

	@Override
	public void setScale(float scale) {
		setScale(new Vector3f(scale,scale,scale));
	}

	public Frustum getFrustum() {
		return frustum;
	}

	@Override
	public void setScale(Vector3f scale) {
	}

	@Override
	public Matrix4f getModelMatrix() {
		return new Matrix4f();
	}

	public FloatBuffer getProjectionMatrixAsBuffer() {
		return projectionMatrixBuffer.asReadOnlyBuffer();
	}
	public FloatBuffer getViewMatrixAsBuffer() {
		return viewMatrixBuffer.asReadOnlyBuffer();
	}

	@Override
	public boolean isSelected() {
		return selected;
	}

	@Override
	public void setSelected(boolean selected) {
		this.selected = selected;
	}

}
