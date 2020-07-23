package de.hanno.hpengine.engine.transform;

import de.hanno.hpengine.util.Parentable;
import de.hanno.hpengine.util.Util;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.io.Serializable;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static de.hanno.hpengine.util.UtilKt.isEqualTo;

public class Transform<T extends Transform> extends Matrix4f implements Parentable<T>, Serializable {
	private static final long serialVersionUID = 1L;

	public static final Vector3f WORLD_RIGHT = new Vector3f(1,0,0);
	public static final Vector3f WORLD_UP = new Vector3f(0,1,0);
	public static final Vector3f WORLD_VIEW = new Vector3f(0,0,1);

	private T parent;
	private List<T> children = new ArrayList<>();

    public Transform() {
		identity();
	}

	public Transform(Transform source) {
		this.set(source);
	}

	public void setOrientation(Quaternionf rotation) {
		Vector3f eulerAngles = new Vector3f();
		rotation.getEulerAnglesXYZ(eulerAngles);
		setRotationXYZ(eulerAngles.x(), eulerAngles.y(), eulerAngles.z());
    }

	public T getParent() {
		return parent;
	}
	public void setParent(T node) {
    	if(hasParent() && getParent().nodeAlreadyParentedSomewhere(node)) { throw new IllegalStateException("Cannot parent myself"); }
		this.parent = node;
	}
	protected boolean nodeAlreadyParentedSomewhere(T node) {
    	if(hasParent()) {
    		return getParent().nodeAlreadyParentedSomewhere(node);
		} else return node == this;
	}

	public List<T> getChildren() {
    	return children;
	}

	public Matrix4f getTransformation() {
		recalculateIfDirty();
		return parent != null ? new Matrix4f(parent.getTransformation()).mul(this) : this;
	}
	protected Matrix4f getTransformationWithoutRecalculation() {
		return parent != null ? new Matrix4f(parent.getTransformationWithoutRecalculation()).mul(this) : this;
	}

	public void recalculateIfDirty() {
		recalculate();
	}
	public void recalculate() {
		for (int i = 0; i < getChildren().size(); i++) {
			getChildren().get(i).recalculate();
		}
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

	Vector3f position = new Vector3f();
    public Vector3f getPosition() {
        return getTransformation().getTranslation(position);
    }

    public Vector3f getRightDirection() {
        return this.transformDirection(new Vector3f(1,0,0)).normalize();
    }

    public Vector3f getUpDirection() {
        return this.transformDirection(new Vector3f(0,1,0)).normalize();
    }

    public Vector3f getViewDirection() {
        return this.transformDirection(new Vector3f(0,0,1)).normalize();
    }

    public Quaternionf getRotation() {
        Quaternionf rotation = new Quaternionf();
        return getTransformation().getNormalizedRotation(rotation);
    }

    public Vector3f getScale() {
        Vector3f scale = new Vector3f();
        return getTransformation().getScale(scale);
    }

    public Vector3f getCenter() {
        Vector3f position = new Vector3f();
        return getTransformation().getTranslation(position);
    }

	public void rotate(Vector4f axisAngle) {
		rotate(axisAngle.w, axisAngle.x, axisAngle.y, axisAngle.z);
	}

	public void rotate(Vector3f axis, int angleInDegrees) {
		rotate((float) Math.toRadians(angleInDegrees), axis.x, axis.y, axis.z);
	}

	public void rotateAround(Vector3f axis, float angleInRad, Vector3f pivot) {
		rotateAround(new Quaternionf().setAngleAxis(angleInRad, axis.x, axis.y, axis.z), pivot.x, pivot.y, pivot.z);
	}
}
