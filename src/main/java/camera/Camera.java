package camera;

import config.Config;
import engine.Transform;
import engine.World;
import engine.model.Entity;
import engine.model.Transformable;
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

public class Camera {
	private static Logger LOGGER = getLogger();

	FloatBuffer projectionMatrixBuffer = BufferUtils.createFloatBuffer(16);
	FloatBuffer viewMatrixBuffer = BufferUtils.createFloatBuffer(16);
	FloatBuffer lastViewMatrixBuffer = BufferUtils.createFloatBuffer(16);

	Transform transform = new Transform();
	
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

	public void updateShadow() {
		transform();
		storeMatrices();
	}
	
	private void storeMatrices() {
		synchronized (this) {
			projectionMatrixBuffer.rewind();
			projectionMatrix.store(projectionMatrixBuffer); projectionMatrixBuffer.flip();
			viewMatrixBuffer.rewind();
			viewMatrix.store(viewMatrixBuffer); viewMatrixBuffer.flip();
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

	public Frustum getFrustum() {
		return frustum;
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

	public Transform getTransform() {
		return transform;
	}

	public void setTransform(Transform transform) {
		this.transform = transform;
	}

}
