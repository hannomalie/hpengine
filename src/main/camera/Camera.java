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
	
	Transform transform = new Transform();
	private float rotationDelta = 45f;
	private float scaleDelta = 0.1f;
	private float posDelta = 180f;
	
	private Matrix4f projectionMatrix = null;
	private Matrix4f viewMatrix = null;

	private Renderer renderer;

	private Frustum frustum;

	private boolean selected;
	private String name;

	private float near;

	private float far;
	
	public Camera(Renderer renderer) {
		this(renderer, Util.createPerpective(60f, (float)Renderer.WIDTH / (float)Renderer.HEIGHT, 0.1f, 5000f), 0.1f, 5000f);
		//this(renderer, Util.createOrthogonal(-1f, 1f, -1f, 1f, -1f, 2f), Util.lookAt(new Vector3f(1,10,1), new Vector3f(0,0,0), new Vector3f(0, 1, 0)));
	}
	
	public Camera(Renderer renderer, Matrix4f projectionMatrix, float near, float far) {
		this(renderer, projectionMatrix, new Matrix4f(), near, far);
	}
	
	public Camera(Renderer renderer, Matrix4f projectionMatrix, Matrix4f viewMatrix, float near, float far) {
		this.name = "Camera_" +  System.currentTimeMillis();
		this.near = near;
		this.far = far;
		this.renderer = renderer;
		this.projectionMatrix = projectionMatrix;

		this.viewMatrix = viewMatrix;

		frustum = new Frustum(this);
	}

	public void update(float seconds) {
		transform();
		updateControls(seconds);
		storeMatrices();
	}
	public void updateShadow() {
		transform();
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
		setViewMatrix(calculateCurrentViewMatrix());
		return viewMatrix;
	}

	public void setViewMatrix(Matrix4f viewMatrix) {
		this.viewMatrix = viewMatrix;
	}

	public FloatBuffer getProjectionMatrixAsBuffer() {
		return projectionMatrixBuffer.asReadOnlyBuffer();
	}
	public FloatBuffer getViewMatrixAsBuffer() {
		return viewMatrixBuffer.asReadOnlyBuffer();
	}

	@Override
	public String getName() {
		return name;
	}
	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public Material getMaterial() {
		return null;
	}

	public float getRotationSpeed() {
		return rotationDelta;
	}

	public Frustum getFrustum() {
		return frustum;
	}

	@Override
	public Matrix4f getModelMatrix() {
		return new Matrix4f();
	}
	@Override
	public boolean isSelected() {
		return selected;
	}
	@Override
	public void setSelected(boolean selected) {
		this.selected = selected;
	}
	@Override
	public Transform getTransform() {
		return transform;
	}

	@Override
	public void setTransform(Transform transform) {
		this.transform = transform;
	}

	public float getNear() {
		return near;
	}

	public float getFar() {
		return far;
	}

}