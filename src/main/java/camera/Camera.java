package camera;

import config.Config;
import engine.Transform;
import engine.World;
import engine.model.Entity;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import renderer.Renderer;
import renderer.material.Material;
import util.Util;

import java.nio.FloatBuffer;
import java.util.logging.Logger;

import static log.ConsoleLogger.getLogger;

public class Camera extends Entity {
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
	private float ratio = (float) Config.WIDTH / (float)Config.HEIGHT;

	private boolean isPerspective = true;
	
	public Camera(Renderer renderer) {
		this(renderer, Util.createPerpective(60f, (float)Config.WIDTH / (float)Config.HEIGHT, 0.1f, 5000f), 0.1f, 5000f, 60f, (float)Config.WIDTH / (float)Config.HEIGHT);
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
		storeMatrices();
	}

	@Override
	public void setWorld(World world) {
		this.world = world;
	}

	@Override
	public World getWorld() {
		return world;
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
		

		float rotationAmount = 1.1f * turbo*rotationDelta * seconds * World.CAMERA_SPEED;
		if (Mouse.isButtonDown(0)) {
			rotate(Transform.WORLD_UP, Mouse.getDX() * rotationAmount);
		}
		if (Mouse.isButtonDown(1)) {
			rotate(Transform.WORLD_RIGHT, -Mouse.getDY() * rotationAmount);
		}
		if (Mouse.isButtonDown(2)) {
			rotate(Transform.WORLD_VIEW, Mouse.getDX() * rotationAmount);
		}
		
		float moveAmount = turbo*posDelta * seconds * World.CAMERA_SPEED;
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
	
	public Matrix4f calculateCurrentViewMatrix() {
		viewMatrix = transform.getTranslationRotation();
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

	public boolean isPerspective() {
		return isPerspective;
	}

	public void setPerspective(boolean isPerspective) {
		this.isPerspective = isPerspective;
	}

	private void calculateProjectionMatrix() {
		if(isPerspective) {
			projectionMatrix = Util.createPerpective(fov, ratio, near, far);	
		} else {
			// TODO: IMPLEMENT ME!
		}
	}

	public void setRatio(float ratio) {
		this.ratio = ratio;
	}

	@Override
	public Transform getTransform() {
		return transform;
	}

	@Override
	public void setTransform(Transform transform) {
		this.transform = transform;
	}

}
