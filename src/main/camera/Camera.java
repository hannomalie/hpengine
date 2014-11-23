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
	FloatBuffer lastViewMatrixBuffer = BufferUtils.createFloatBuffer(16);
	
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
	private float fov = 60;
	private float ratio = (float)Renderer.WIDTH / (float)Renderer.HEIGHT;
	
	public Camera(Renderer renderer) {
		this(renderer, Util.createPerpective(60f, (float)Renderer.WIDTH / (float)Renderer.HEIGHT, 0.1f, 5000f), 0.1f, 5000f, 60f, (float)Renderer.WIDTH / (float)Renderer.HEIGHT);
		//this(renderer, Util.createOrthogonal(-1f, 1f, -1f, 1f, -1f, 2f), Util.lookAt(new Vector3f(1,10,1), new Vector3f(0,0,0), new Vector3f(0, 1, 0)));
	}
	
	public Camera(Renderer renderer, Matrix4f projectionMatrix, float near, float far, float fov, float ratio) {
		this(renderer, projectionMatrix, new Matrix4f(), near, far, fov, ratio);
	}
	
	public Camera(Renderer renderer, Matrix4f projectionMatrix, Matrix4f viewMatrix, float near, float far, float fov, float ratio) {
		this.name = "Camera_" +  System.currentTimeMillis();
		this.near = near;
		this.far = far;
		this.fov = fov;
		this.ratio = ratio;
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
		if(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
			turbo = 3f;
		}
		

		float rotationAmount = 1.1f * turbo*rotationDelta * seconds;
		if (Mouse.isButtonDown(0)) {
			rotate(Transform.WORLD_UP, Mouse.getDX() * rotationAmount);
		}
		if (Mouse.isButtonDown(1)) {
			rotate(Transform.WORLD_RIGHT, -Mouse.getDY() * rotationAmount);
		}
		if (Mouse.isButtonDown(2)) {
			rotate(Transform.WORLD_VIEW, Mouse.getDX() * rotationAmount);
		}
		
		float moveAmount = turbo*posDelta * seconds;
		if (Keyboard.isKeyDown(Keyboard.KEY_W)) {
			move(new Vector3f(0,0,moveAmount));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_A)) {
			move(new Vector3f(moveAmount, 0, 0));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_S)) {
			move(new Vector3f(0, 0, -moveAmount));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_D)) {
			move(new Vector3f(-moveAmount, 0, 0));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_Q)) {
			move(new Vector3f(0, moveAmount, 0));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_E)) {
			move(new Vector3f(0, -moveAmount, 0));
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
	public FloatBuffer getLastViewMatrixAsBuffer() {
		return lastViewMatrixBuffer;
	}

	public void setViewMatrix(Matrix4f viewMatrix) {
		this.viewMatrix = viewMatrix;
	}
	
	public void saveViewMatrixAsLastViewMatrix() {
		lastViewMatrixBuffer.rewind();
		lastViewMatrixBuffer.put(getViewMatrixAsBuffer());
		lastViewMatrixBuffer.rewind();
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

	public void setNear(float near) {
		this.near = near;
		calculateProjectionMatrix();
		frustum.calculate(this);
	}


	public void setFar(float far) {
		this.far = far;
		calculateProjectionMatrix();
		frustum.calculate(this);
	}

	public float getFar() {
		return far;
	}

	private void calculateProjectionMatrix() {
		projectionMatrix = Util.createPerpective(fov, ratio, near, far);
	}

}
