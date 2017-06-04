package de.hanno.hpengine.engine;

import de.hanno.hpengine.engine.model.Transformable;
import org.lwjgl.BufferUtils;
import de.hanno.hpengine.util.Util;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class Transform implements Serializable, Transformable {
	private static final long serialVersionUID = 1L;

	public static final Vector3f WORLD_RIGHT = new Vector3f(1,0,0);
	public static final Vector3f WORLD_UP = new Vector3f(0,1,0);
	public static final Vector3f WORLD_VIEW = new Vector3f(0,0,-1);
	public static final Vector4f WORLD_RIGHT_V4 = new Vector4f(1,0,0,0);
	public static final Vector4f WORLD_UP_V4 = new Vector4f(0,1,0,0);
	public static final Vector4f WORLD_VIEW_V4 = new Vector4f(0,0,-1,0);
	public static final Vector4f RIGHT_V4 = new Vector4f(1,0,0,0);
	public static final Vector4f UP_V4 = new Vector4f(0,1,0,0);
	public static final Vector4f VIEW_V4 = new Vector4f(0,0,-1,0);
	public static Vector3f IDENTITY_LOCAL_WORLD = new Vector3f(1,1,1);
	public static Vector4f IDENTITY_LOCAL_WORLD_V4 = new Vector4f(1,1,1,1);
	
	private Transform parent;
	private Matrix4f parentMatrix = null;
	private List<Transform> children = new ArrayList<>();
	private Vector3f position = new Vector3f();
	private Vector3f scale = new Vector3f(1,1,1);
	private Quaternionf orientation = new Quaternionf();

	transient private boolean isDirty = true;
	transient private boolean hasMoved = true;
	transient Matrix4f translationRotation = new Matrix4f();
	transient Matrix4f viewMatrix = new Matrix4f();
	transient Matrix4f transformation = new Matrix4f();

	transient protected FloatBuffer modelMatrixBuffer;
	transient protected FloatBuffer viewMatrixBuffer;

	private transient Vector3f tempVec3;

    public Transform() {
		orientation.identity();
		modelMatrixBuffer = BufferUtils.createFloatBuffer(16);
		modelMatrixBuffer.rewind();
		viewMatrixBuffer = BufferUtils.createFloatBuffer(16);
		viewMatrixBuffer.rewind();
		parentMatrix = new Matrix4f();
	}

    public Transform init(Transform other) {
        this.setPosition(other.getPosition());
        this.setOrientation(other.getOrientation());
        this.setScale(other.getmul());
        if(other.getParent() != null) {
			this.setParent(other.getParent());
		}
        for(Transform currentChild : other.getChildren()) {
            this.addChild(currentChild);
        }
        return this;
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

	private Vector3f tempV3 = new Vector3f();
	private Vector3f setAndReturnCopy(Vector3f in) {
//		tempVec3.set(in.x, in.y, in.z);
//		return tempVec3;
		return tempV3.set(in);
	}
	private Vector4f tempV4 = new Vector4f();
	private Vector4f setAndReturnCopy(Vector4f in) {
//		tempVec3.set(in.x, in.y, in.z);
//		return tempVec3;
		return tempV4.set(in);
	}
	private Vector3f tempV3_0 = new Vector3f();
	private Vector3f setAndReturnCopyAsVec3(Vector4f in) {
//		tempVec3.set(in.x, in.y, in.z);
//		return tempVec3;
		tempV3_0.x = in.x;
		tempV3_0.y = in.y;
		tempV3_0.z = in.z;
		return tempV3_0;
	}
	Quaternionf tempQuat = new Quaternionf();
	private Quaternionf setAndReturnCopy(Quaternionf in) {
//		tempQuat.set(in.x, in.y, in.z, in.w);
//		return tempQuat;
		return tempQuat.set(in);
	}

	public List<Transform> getChildren() {
		return children;
	}

    @Override
    public Transform getTransform() {
        return this;
    }

    @Override
    public void setTransform(Transform transform) {
        this.init(transform);
    }

    public Vector3f getPosition() {
		return setAndReturnCopy(position);
	}
	public Vector3f getWorldPosition() {
		if(parent != null) {
			Vector4f temp = new Vector4f(position.x, position.y, position.z, 1).mul(new Matrix4f(parent.getTransformation()));
			return setAndReturnCopyAsVec3(temp);
		}
		return getPosition();
	}
	public void setPosition(Vector3f position) {
        if(this.position.equals(position)) { return; }
		this.position.set(position);
		setDirty(true);
	}
	public Vector3f getmul() {
		return setAndReturnCopy(scale);
	}
	public Vector3f getWorldmul() {
		if(parent != null) {
			return new Vector3f(parent.getWorldmul()).mul(scale);
		}
		return getmul();
	}
	public void setScale(Vector3f mul) {
        if(this.scale.equals(mul)) { return; }
		this.scale.set(mul);
		setDirty(true);
	}
	public void setScale(float mul) {
		setScale(new Vector3f(mul, mul, mul));
	}
	public Vector3f getScale() {
		return scale;
	}

	public Quaternionf getOrientation() {
		return setAndReturnCopy(orientation);
	}
	public Quaternionf getWorldOrientation() {
		if(parent != null) {
            Quaternionf result = new Quaternionf((parent.getWorldOrientation()).mul(orientation));
            return result;
		}
		return getOrientation();
	}
	public void setOrientation(Quaternionf orientation) {
        if(Util.equals(this.orientation, orientation)) { return; }
		setDirty(true);
		this.orientation.set(orientation);
	}

	private final Vector3f temp_viewVec = new Vector3f();
	private final Vector4f temp_viewVec4 = new Vector4f();
	public Vector3f getViewDirection() {
		Vector4f temp = new Matrix4f(getTransformation()).transform(VIEW_V4, temp_viewVec4);
		temp_viewVec.set(temp.x, temp.y, temp.z);
		return temp_viewVec.normalize();
	}
	private final Vector3f temp_rightVec = new Vector3f();
	private final Vector4f temp_rightVec4 = new Vector4f();
	public Vector3f getRightDirection() {
		Vector4f temp = RIGHT_V4.mul(getTransformation(), temp_rightVec4);
		temp_rightVec.set(temp.x, temp.y, temp.z);
		return temp_rightVec.normalize();
	}
	private final Vector3f temp_upVec = new Vector3f();
	private final Vector4f temp_upVec4 = new Vector4f();
	public Vector3f getUpDirection() {
		Vector4f temp = UP_V4.mul(getTransformation(), temp_upVec4);
		temp_upVec.set(temp.x, temp.y, temp.z);
		return temp_upVec.normalize();
	}
	public void rotate(Vector3f axis, float angleInDegrees) {
		rotate(new Vector4f(axis.x, axis.y, axis.z, angleInDegrees));
		setDirty(true);
	}
	public void rotate(Vector4f axisAngle) {
		rotateWorld(worldDirectionToLocal(new Vector3f(axisAngle.x, axisAngle.y, axisAngle.z)), axisAngle.w);
		setDirty(true);
	}
	public void rotateWorld(Vector4f axisAngle) {
		rotateWorld(new Vector3f(axisAngle.x, axisAngle.y, axisAngle.z), axisAngle.w);
		setDirty(true);
	}
	public void rotateWorld(Vector3f axis, float angleInDegrees) {
		axis = localDirectionToWorld(axis);
		Quaternionf temp = new Quaternionf();
		temp.fromAxisAngleRad(axis.x, axis.y, axis.z, (float) Math.toRadians(angleInDegrees));
		setOrientation(new Quaternionf(getOrientation()).mul(temp));
		setDirty(true);
	}
	public void move(Vector3f amount) {
		Vector3f combined = new Vector3f();
		getRightDirection().mul(amount.x, combined);
		combined.add(new Vector3f(getUpDirection()).mul(amount.y), combined);
		combined.add(new Vector3f(getViewDirection()).mul(-amount.z), combined);
		moveInWorld(combined);
	}
	private final Vector3f temp_moveInWorld = new Vector3f();
	public void moveInWorld(Vector3f amount) {
		setPosition(new Vector3f(getPosition()).add(amount, temp_moveInWorld));
		setDirty(true);
	}

	public Vector3f localPositionToWorld(Vector3f localPosition) {
		return localToWorld(new Vector4f(localPosition.x, localPosition.y, localPosition.z, 1f));
	}

	private Matrix4f tempOrientationLocalToWorldMatrix = new Matrix4f();
	public Vector3f localDirectionToWorld(Vector3f localAxis) {
		return new Vector3f(localAxis).rotate(getOrientation()).negate();
	}
	private Matrix4f tempOrientationWorldToLocalMatrix = new Matrix4f();
	public Vector3f worldDirectionToLocal(Vector3f worldAxis) {
		return new Vector3f(worldAxis).rotate(getOrientation());
	}
	
	public Vector3f localToWorld(Vector4f homogenVector) {
		Vector4f temp = new Vector4f(homogenVector).mul(getTranslationRotation().invert());
		return new Vector3f(temp.x, temp.y, temp.z);
	}

	public Matrix4f getTranslationRotation() {
		recalculateIfDirty();
		return translationRotation;
	}

	public Matrix4f getTransformation() {
		recalculateIfDirty();
		return transformation;
	}

	private final Matrix4f tempTransformationMatrix = new Matrix4f();
	private final Matrix4f tempOrientationMatrix = new Matrix4f();
	private final Matrix4f calculateTransformation() {
		tempTransformationMatrix.identity();
        tempTransformationMatrix.setTranslation(position);
		tempTransformationMatrix.rotate(orientation);
		tempTransformationMatrix.scale(getScale());
		if(parent != null) {
			parentMatrix = parent.getTransformation();
            tempTransformationMatrix.mul(parentMatrix);
		}

		return tempTransformationMatrix;
	}

	private final Matrix4f tempTranslationRotationMatrix = new Matrix4f();
	private final Matrix4f calculateTranslationRotation() {
		tempTranslationRotationMatrix.identity();
        tempTranslationRotationMatrix.setTranslation(position);
		tempTranslationRotationMatrix.rotate(orientation);

		if(parent != null) {
			parentMatrix = parent.getTranslationRotation();
            tempTranslationRotationMatrix.mul(parentMatrix);
		}

		return tempTranslationRotationMatrix;
	}

	private Matrix4f tempViewMatrix = new Matrix4f();
	private Matrix4f calculateViewMatrix() {
		tempViewMatrix.set(translationRotation).invert();
		return tempViewMatrix;
	}

	public void recalculateIfDirty() {
		if(isDirty() || translationRotation == null || transformation == null) {
			recalculate();
		}
	}
	public void recalculate() {
		setDirty(false);
		translationRotation = calculateTranslationRotation();
		viewMatrix = calculateViewMatrix();
		transformation = calculateTransformation();
			if(children == null) {
				children = new ArrayList<>();
			}
			for (int i = 0; i < children.size(); i++) {
				children.get(i).recalculate();
			}
		bufferMatrixes();
		hasMoved = true;
	}

	public boolean isDirty() {
		return isDirty;
	}
	public void setDirty(boolean isDirty) {
		this.isDirty = isDirty;
		setHasMoved(true);
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

	private boolean equals(Vector3f a, Vector3f b) {
		return a.x == b.x && a.y == b.y && a.z == b.z;
	}
	private boolean equals(Quaternionf a, Quaternionf b) {
		return a.x == b.x && a.y == b.y && a.z == b.z && a.w == b.w;
	}

	Matrix4f tempView = new Matrix4f();
	public Matrix4f getViewMatrix() {
		recalculateIfDirty();
		tempView.set(viewMatrix);
		return tempView;
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
			transformation.get(modelMatrixBuffer);
			modelMatrixBuffer.rewind();
		}

		synchronized(viewMatrixBuffer) {
			viewMatrixBuffer.rewind();
			viewMatrix.get(viewMatrixBuffer);
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

	public void setOrientationFromAxisAngle(Vector4f orientationFromAxisAngle) {
		orientation.fromAxisAngleRad(orientationFromAxisAngle.x, orientationFromAxisAngle.y, orientationFromAxisAngle.z, orientationFromAxisAngle.w);
	}
}
