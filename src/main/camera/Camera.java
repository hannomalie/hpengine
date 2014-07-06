package main.camera;

import static main.log.ConsoleLogger.getLogger;

import java.nio.FloatBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import main.Transform;
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
	
	Transform transform = new Transform();
	private float rotationDelta = 45f;
	private float scaleDelta = 0.1f;
	private float posDelta = 180f;
	
	Vector3f scaleAddResolution = new Vector3f(scaleDelta, scaleDelta, scaleDelta);
	Vector3f scaleMinusResolution = new Vector3f(-scaleDelta, -scaleDelta, -scaleDelta);
	
	private Matrix4f projectionMatrix = null;
	private Matrix4f viewMatrix = null;

	private Renderer renderer;

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

		frustum = new Frustum(this);
	}

	public void update(float seconds) {
//		System.out.println("View " + transform.getViewDirection());
//		System.out.println("Up " + transform.getUpDirection());
//		System.out.println("Right " + transform.getRightDirection());
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
		
		float turbo = 1f;
		if(Keyboard.isKeyDown(Keyboard.KEY_CAPITAL)) {
			turbo = 2;
		}

		if (Mouse.isButtonDown(0)) {
			transform.rotateWorld(transform.getUpDirection(), -Mouse.getDX() * turbo*rotationDelta * seconds);
		}
		if (Mouse.isButtonDown(1)) {
			transform.rotateWorld(transform.getRightDirection(), Mouse.getDY() * turbo*rotationDelta * seconds);
		}
		if (Mouse.isButtonDown(2)) {
			transform.rotateWorld(transform.getViewDirection(), Mouse.getDX() * turbo*rotationDelta * seconds);
		}
		
		float moveAmount = turbo*posDelta * seconds;
		if (Keyboard.isKeyDown(Keyboard.KEY_W)) {
			transform.move(new Vector3f(0,0,moveAmount));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_A)) {
			transform.move(new Vector3f(moveAmount, 0, 0));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_S)) {
			transform.move(new Vector3f(0, 0, -moveAmount));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_D)) {
			transform.move(new Vector3f(-moveAmount, 0, 0));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_Q)) {
			transform.move(new Vector3f(0, moveAmount, 0));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_E)) {
			transform.move(new Vector3f(0, -moveAmount, 0));
		}
	}
	

	private void transform() {
		setViewMatrix(calculateCurrentViewMatrix());
		frustum.calculate(this);
	}
	
	private Matrix4f calculateCurrentViewMatrix() {
		viewMatrix = transform.getTransformation();
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
		return transform.getPosition();
	}

	public void setPosition(Vector3f position) {
		transform.setPosition(position);
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
		return transform.getOrientation();
	}

	@Override
	public void rotate(Vector4f axisAngle) {
		transform.rotate(axisAngle);
		transform();
	}

	public float getRotationSpeed() {
		return rotationDelta;
	}

	public Vector3f getRight() {
		return transform.getRightDirection();
	}
	public Vector3f getUp() {
		return transform.getUpDirection();
	}
	public Vector3f getBack() {
		return (Vector3f) transform.getViewDirection().negate();
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
		transform.getOrientation();
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
