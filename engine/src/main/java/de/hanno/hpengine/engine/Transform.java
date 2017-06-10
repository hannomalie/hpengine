package de.hanno.hpengine.engine;

import org.joml.*;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.Math;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class Transform extends Matrix4f implements Serializable {
	private static final long serialVersionUID = 1L;

	public static final Vector3f WORLD_RIGHT = new Vector3f(1,0,0);
	public static final Vector3f WORLD_UP = new Vector3f(0,1,0);
	public static final Vector3f WORLD_VIEW = new Vector3f(0,0,-1);

	private Transform parent;
	private Matrix4f parentMatrix = null;
	private List<Transform> children = new ArrayList<>();

	transient private boolean hasMoved = true;

	transient protected FloatBuffer modelMatrixBuffer;
	transient protected FloatBuffer viewMatrixBuffer;

	private transient Vector3f tempVec3;
	private transient Quaternionf tempQuat;

    public Transform() {
		identity();
		modelMatrixBuffer = BufferUtils.createFloatBuffer(16);
		modelMatrixBuffer.rewind();
		viewMatrixBuffer = BufferUtils.createFloatBuffer(16);
		viewMatrixBuffer.rewind();
		parentMatrix = new Matrix4f();
	}

	public Transform(Transform downFacing) {
		this.set(downFacing);
	}

	public Transform init(Transform other) {
		set(other);
        if(other.getParent() != null) {
			this.setParent(other.getParent());
		}
        for(Transform currentChild : other.getChildren()) {
            this.addChild(currentChild);
        }
        return this;
    }

    public void setOrientation(Quaternionf rotation) {
		Vector3f eulerAngles = new Vector3f();
		rotation.getEulerAnglesXYZ(eulerAngles);
		setRotationXYZ(eulerAngles.x(), eulerAngles.y(), eulerAngles.z());
    }

	public Transform getParent() {
		return parent;
	}
	public void setParent(Transform parent) {
		this.parent = parent;
		parent.addChild(this);
	}
	public void addChild(Transform transform) {
		if(children == null) {
			children = new ArrayList<>();
		}

		children.add(transform);
	}

	public List<Transform> getChildren() {
		return children;
	}


	public Matrix4f getTransformation() {
		recalculateIfDirty();
		return this;
	}

	public void recalculateIfDirty() {
		recalculate();
	}
	public void recalculate() {
		if(children == null) {
			children = new ArrayList<>();
		}
		for (int i = 0; i < children.size(); i++) {
			children.get(i).recalculate();
		}
		bufferMatrixes();
		hasMoved = true;
	}

	public boolean isHasMoved() {
		if(parent != null && parent.isHasMoved()) { return true; }
		return hasMoved;
	}
	public void setHasMoved(boolean hasMoved) {
		this.hasMoved = hasMoved;
	}
	
	@Override
	public boolean equals(Object b) {
		if(!(b instanceof Transform)) {
			return false;
		}
		Transform other = (Transform) b;
		
		return (equals(getPosition(), other.getPosition()) && equals(getOrientation(), other.getOrientation()));
	}

    public Quaternionf getOrientation() {
        return getRotation();
    }

    private boolean equals(Vector3f a, Vector3f b) {
		return a.x == b.x && a.y == b.y && a.z == b.z;
	}
	private boolean equals(Quaternionf a, Quaternionf b) {
		return a.x == b.x && a.y == b.y && a.z == b.z && a.w == b.w;
	}

	Matrix4f tempView = new Matrix4f();
	public Matrix4f getViewMatrix() {
		return new Matrix4f(this).invert();
	}

	public void init() {
		modelMatrixBuffer = BufferUtils.createFloatBuffer(16);
		modelMatrixBuffer.rewind();
		viewMatrixBuffer = BufferUtils.createFloatBuffer(16);
		viewMatrixBuffer.rewind();
		tempVec3 = new Vector3f();
		tempQuat = new Quaternionf();
		recalculate();
	}

	protected void bufferMatrixes() {
		synchronized(modelMatrixBuffer) {
			modelMatrixBuffer.rewind();
			this.get(modelMatrixBuffer);
			modelMatrixBuffer.rewind();
		}

		synchronized(viewMatrixBuffer) {
			viewMatrixBuffer.rewind();
			getViewMatrix().get(viewMatrixBuffer);
			viewMatrixBuffer.rewind();
		}
	}

	public FloatBuffer getTransformationBuffer() {
		recalculateIfDirty();
		return modelMatrixBuffer;
	}
	public FloatBuffer getTranslationRotationBuffer(boolean recalculateBefore) {
		if(recalculateBefore) {
			recalculateIfDirty();
		}
		return viewMatrixBuffer;
	}

	private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
		stream.defaultReadObject();
		modelMatrixBuffer = BufferUtils.createFloatBuffer(16);
		modelMatrixBuffer.rewind();
		viewMatrixBuffer = BufferUtils.createFloatBuffer(16);
		viewMatrixBuffer.rewind();
	}

    public Vector3f getPosition() {
        Vector3f position = new Vector3f();
        return this.getTranslation(position);
    }

    public Vector3f getRightDirection() {
        return this.transformDirection(new Vector3f(1,0,0)).normalize();
    }

    public Vector3f getUpDirection() {
        return this.transformDirection(new Vector3f(0,1,0)).normalize();
    }

    public Vector3f getViewDirection() {
        return this.transformDirection(new Vector3f(0,0,-1)).normalize();
    }

    public Quaternionf getRotation() {
        Quaternionf rotation = new Quaternionf();
        return getNormalizedRotation(rotation);
    }

    public Vector3f getScale() {
        Vector3f scale = new Vector3f();
        return getScale(scale);
    }

    public Vector3f getCenter() {
        Vector3f position = new Vector3f();
        return getTranslation(position);
    }

	public void rotate(Vector4f axisAngle) {
		rotate(axisAngle.w, axisAngle.x, axisAngle.y, axisAngle.z);
	}

	public void rotate(Vector3f axis, int angleInDegrees) {
		rotate((float) Math.toRadians(angleInDegrees), axis.x, axis.y, axis.z);
	}
}
