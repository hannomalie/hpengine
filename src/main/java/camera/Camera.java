package camera;

import config.Config;
import engine.World;
import engine.model.Entity;
import engine.model.Model;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import renderer.material.MaterialFactory;
import util.Util;

import java.nio.FloatBuffer;
import java.util.logging.Logger;

import static log.ConsoleLogger.getLogger;

public class Camera extends Entity {
	private static Logger LOGGER = getLogger();

	FloatBuffer viewProjectionMatrixBuffer = BufferUtils.createFloatBuffer(16);
	FloatBuffer projectionMatrixBuffer = BufferUtils.createFloatBuffer(16);
	FloatBuffer lastViewMatrixBuffer = BufferUtils.createFloatBuffer(16);

	protected Matrix4f projectionMatrix = null;
	protected Matrix4f viewProjectionMatrix = null;

	protected Frustum frustum;

	private float near = 0.1f;
	private float far = -5000f;
	private float fov = 60f;
	private float ratio = (float) Config.WIDTH / (float)Config.HEIGHT;
	private float width = 1600f;
	private float height = 1600f;

	private boolean isPerspective = true;
	
	public Camera() {
		this(Util.createPerpective(60f, (float)Config.WIDTH / (float)Config.HEIGHT, 0.1f, 5000f), 0.1f, 5000f, 60f, (float)Config.WIDTH / (float)Config.HEIGHT);
		//this(renderer, Util.createOrthogonal(-1f, 1f, -1f, 1f, -1f, 2f), Util.lookAt(new Vector3f(1,10,1), new Vector3f(0,0,0), new Vector3f(0, 1, 0)));
	}
	public Camera(float near, float far, float fov, float ratio) {
		this(Util.createPerpective(fov, ratio, near, far), near, far, fov, ratio);
	}

	public Camera(Matrix4f projectionMatrix, float near, float far, float fov, float ratio) {
		this.name = "Camera_" +  System.currentTimeMillis();
		this.near = near;
		this.far = far;
		this.fov = fov;
		this.ratio = ratio;
		this.projectionMatrix = projectionMatrix;

		frustum = new Frustum(this);
	}

	public Camera(World world, MaterialFactory materialFactory, Vector3f position, String name, Model model, String material) {
		super(materialFactory, position, name, model, material);
	}

	public void update(float seconds) {
		super.update(seconds);
		transform();
		storeMatrices();
	}

	private void storeMatrices() {
		synchronized (projectionMatrixBuffer) {
			projectionMatrixBuffer.rewind();
			projectionMatrix.store(projectionMatrixBuffer);
			projectionMatrixBuffer.flip();
		}
		synchronized (viewProjectionMatrixBuffer) {

			viewProjectionMatrixBuffer.rewind();
			Matrix4f.mul(projectionMatrix, getViewMatrix(), null).store(viewProjectionMatrixBuffer);
			viewProjectionMatrixBuffer.flip();
		}
	}
	

	private void transform() {
		frustum.calculate(this);
	}
	
	public Matrix4f getProjectionMatrix() {
		return projectionMatrix;
	}

	public void setProjectionMatrix(Matrix4f projectionMatrix) {
		this.projectionMatrix = projectionMatrix;
	}

	public FloatBuffer getLastViewMatrixAsBuffer() {
		return lastViewMatrixBuffer;
	}

	public void saveViewMatrixAsLastViewMatrix() {
		lastViewMatrixBuffer.rewind();
		lastViewMatrixBuffer.put(getViewMatrixAsBuffer());
		lastViewMatrixBuffer.rewind();
	}

	public FloatBuffer getProjectionMatrixAsBuffer() {
		return projectionMatrixBuffer.asReadOnlyBuffer();
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

	public float getWidth() {
		return width;
	}

	public void setWidth(float width) {
		calculateProjectionMatrix();
		frustum.calculate(this);
		this.width = width;
	}

	public float getHeight() {
		return height;
	}

	public void setHeight(float height) {
		calculateProjectionMatrix();
		frustum.calculate(this);
		this.height = height;
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
			projectionMatrix = Util.createOrthogonal(-width/2, width/2, height/2, -height/2, -far/2, far/2);
		}
	}

	public void setRatio(float ratio) {
		this.ratio = ratio;
	}

	public void setFov(float fov) {
		this.fov = fov;
	}

	public float getFov() {
		return fov;
	}

	public float getRatio() {
		return ratio;
	}

	public FloatBuffer getViewProjectionMatrixAsBuffer() {
		return viewProjectionMatrixBuffer;
	}
}
