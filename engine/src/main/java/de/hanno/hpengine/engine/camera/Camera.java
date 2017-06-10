package de.hanno.hpengine.engine.camera;

import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.Model;
import de.hanno.hpengine.log.ConsoleLogger;
import de.hanno.hpengine.util.Util;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.FloatBuffer;
import java.util.logging.Logger;

public class Camera extends Entity {
	private static Logger LOGGER = ConsoleLogger.getLogger();

	transient FloatBuffer viewProjectionMatrixBuffer = BufferUtils.createFloatBuffer(16);
	transient FloatBuffer projectionMatrixBuffer = BufferUtils.createFloatBuffer(16);
	transient FloatBuffer lastViewMatrixBuffer = BufferUtils.createFloatBuffer(16);

	protected Matrix4f projectionMatrix = new Matrix4f();
	protected Matrix4f viewProjectionMatrix = null;

	protected Frustum frustum = new Frustum();

	private float near = 10.1f;
	private float far = -5000f;
	private float fov = 30f;
	private float ratio = (float) Config.getInstance().getWidth() / (float) Config.getInstance().getHeight();
	private float width = 1600f;
	private float height = 1600f;

	private boolean isPerspective = true;

	public Camera() {
		this.name = "Camera_" +  System.currentTimeMillis();
        init(Util.createPerpective(45f, (float) Config.getInstance().getWidth() / (float) Config.getInstance().getHeight(), 0.1f, 5000f), 0.1f, 5000f, 60f, (float) Config.getInstance().getWidth() / (float) Config.getInstance().getHeight());
		//this(renderer, Util.createOrthogonal(-1f, 1f, -1f, 1f, -1f, 2f), Util.lookAt(new Vector3f(1,10,1), new Vector3f(0,0,0), new Vector3f(0, 1, 0)));
	}
	public Camera(float near, float far, float fov, float ratio) {
		this.name = "Camera_" +  System.currentTimeMillis();
        init(Util.createPerpective(fov, ratio, near, far), near, far, fov, ratio);
	}

    public Camera(Camera camera) {
		this.name = "Camera_" +  System.currentTimeMillis();
        init(camera);
    }


	public Camera(Matrix4f projectionMatrix, float near, float far, float fov, float ratio) {
		this.name = "Camera_" +  System.currentTimeMillis();
        init(projectionMatrix, near, far, fov, ratio);
	}

    public void init(Camera camera) {
		set(camera);
        init(camera.getProjectionMatrix(), camera.getNear(), camera.getFar(), camera.getFov(), camera.getRatio());
        if(camera.hasParent()) {
            setParent(camera.getParent());
        }
    }

    public void init(Matrix4f projectionMatrix, float near, float far, float fov, float ratio) {
        this.near = near;
        this.far = far;
        this.fov = fov;
        this.ratio = ratio;
        this.projectionMatrix.set(projectionMatrix);

        frustum.calculate(this);
        saveViewMatrixAsLastViewMatrix();
        transform();
        storeMatrices();
    }

    public Camera(Vector3f position, String name, Model model) {
		super(position, name, model);
        saveViewMatrixAsLastViewMatrix();
        transform();
        storeMatrices();
	}

	public void update(float seconds) {
		saveViewMatrixAsLastViewMatrix();
		super.update(seconds);
        if(hasMoved()) {
            transform();
            storeMatrices();
        }
	}

	private void storeMatrices() {
		synchronized (projectionMatrixBuffer) {
			projectionMatrixBuffer.rewind();
			projectionMatrix.get(projectionMatrixBuffer);
			projectionMatrixBuffer.rewind();
		}
		synchronized (viewProjectionMatrixBuffer) {
			viewProjectionMatrixBuffer.rewind();
			new Matrix4f(projectionMatrix).mul(getViewMatrix()).get(viewProjectionMatrixBuffer);
			viewProjectionMatrixBuffer.rewind();
		}
	}

	public Matrix4f getViewMatrix() {
		Matrix4f viewMatrix = new Matrix4f();
		return this.invert(viewMatrix);
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
		lastViewMatrixBuffer.put(getViewMatrixAsBuffer(false));
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
		this.width = width;
        calculateProjectionMatrix();
        frustum.calculate(this);
	}

	public float getHeight() {
		return height;
	}

	public void setHeight(float height) {
		this.height = height;
        calculateProjectionMatrix();
        frustum.calculate(this);
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
        calculateProjectionMatrix();
        frustum.calculate(this);
	}

	public void setFov(float fov) {
		this.fov = fov;
        calculateProjectionMatrix();
        frustum.calculate(this);
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


	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		viewProjectionMatrixBuffer = BufferUtils.createFloatBuffer(16);
		projectionMatrixBuffer = BufferUtils.createFloatBuffer(16);
		lastViewMatrixBuffer = BufferUtils.createFloatBuffer(16);
	}


	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.defaultWriteObject();
	}
}
