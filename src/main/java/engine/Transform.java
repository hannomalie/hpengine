package engine;

import org.lwjgl.BufferUtils;
import util.Util;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Transform implements Serializable {
	private static final long serialVersionUID = 1L;
	
	public static Vector3f WORLD_UP = new Vector3f(0,1,0);
	public static Vector3f WORLD_RIGHT = new Vector3f(1,0,0);
	public static Vector3f WORLD_VIEW = new Vector3f(0,0,-1);
	public static Vector4f WORLD_UP_V4 = new Vector4f(0,1,0,0);
	public static Vector4f WORLD_RIGHT_V4 = new Vector4f(1,0,0,0);
	public static Vector4f WORLD_VIEW_V4 = new Vector4f(0,0,-1,0);
	public static Vector3f IDENTITY_LOCAL_WORLD = new Vector3f(1,1,1);
	public static Vector4f IDENTITY_LOCAL_WORLD_V4 = new Vector4f(1,1,1,1);
	
	private transient Transform parent;
	private transient Matrix4f parentMatrix;
	private transient List<Transform> children;
	private Vector3f position = new Vector3f();
	private Vector3f scale = new Vector3f(1,1,1);
	private Quaternion orientation = new Quaternion();

	transient private boolean isDirty = true;
	transient private boolean hasMoved = true;
	transient Matrix4f translationRotation = new Matrix4f();
	transient Matrix4f viewMatrix = new Matrix4f();
	transient Matrix4f transformation = new Matrix4f();

	transient protected FloatBuffer modelMatrixBuffer;
	transient protected FloatBuffer viewMatrixBuffer;
	
	public Transform() {
		modelMatrixBuffer = BufferUtils.createFloatBuffer(16);
		modelMatrixBuffer.rewind();
		viewMatrixBuffer = BufferUtils.createFloatBuffer(16);
		viewMatrixBuffer.rewind();
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

	public Vector3f getLocalPosition() {
		return position;
	}
	public Quaternion getLocalOrientation() {
		return orientation;
	}
	public Vector3f getLocalScale() {
		return scale;
	}

	public Vector3f getPosition() {
		recalculateIfDirty();
		if(parent != null) {
			Vector4f temp = Matrix4f.transform(parent.getTransformation(), new Vector4f(position.x, position.y, position.z, 1), null);
			return new Vector3f(temp.x, temp.y, temp.z);
		}
		return position;
	}
	public void setPosition(Vector3f position) {
		this.position = position;
		setDirty(true);
	}
	public Vector3f getScale() {
		recalculateIfDirty();
		return scale;
	}
	public void setScale(Vector3f scale) {
		this.scale = scale;
		setDirty(true);
	}
	public void setScale(float scale) {
		setScale(new Vector3f(scale, scale, scale));
		setDirty(true);
	}
	public Quaternion getOrientation() {
		recalculateIfDirty();
		if(parent != null) {
			return Quaternion.mul(parent.getOrientation(), orientation, null);
		}
		return orientation;
	}
	public void setOrientation(Quaternion orientation) {
		this.orientation = orientation;
		setDirty(true);
	}
	public Vector3f getViewDirection() {
		Vector4f temp = Matrix4f.transform(Util.toMatrix(getOrientation().negate(null)), WORLD_VIEW_V4, null);
		return (Vector3f) new Vector3f(temp.x, temp.y, temp.z).normalise();
	}
	public Vector3f getRightDirection() {
		Vector4f temp = Matrix4f.transform(Util.toMatrix(getOrientation().negate(null)), WORLD_RIGHT_V4, null);
		return (Vector3f) new Vector3f(temp.x, temp.y, temp.z).normalise();
	}
	public Vector3f getUpDirection() {
		Vector4f temp = Matrix4f.transform(Util.toMatrix(getOrientation().negate(null)), WORLD_UP_V4, null);
		return (Vector3f) new Vector3f(temp.x, temp.y, temp.z).normalise();
	}
	public void rotate(Vector3f axis, float angleInDegrees) {
		rotate(new Vector4f(axis.x, axis.y, axis.z, angleInDegrees));
		setDirty(true);
	}
	public void rotate(Vector4f axisAngle) {
		rotateWorld(localDirectionToWorld(new Vector3f(axisAngle.x, axisAngle.y, axisAngle.z)), axisAngle.w);
		setDirty(true);
	}
	public void rotateWorld(Vector3f axis, float angleInDegrees) {
		Quaternion temp = new Quaternion();
		temp.setFromAxisAngle(new Vector4f(axis.x, axis.y, axis.z, (float) Math.toRadians(angleInDegrees)));
		setOrientation(Quaternion.mul(getOrientation(), temp, null));
		setDirty(true);
	}
	public void rotateWorld(Vector4f axisAngle) {
		rotateWorld(new Vector3f(axisAngle.x,  axisAngle.y, axisAngle.z), axisAngle.w);
		setDirty(true);
	}
	public void move(Vector3f amount) {
//		Vector4f temp = Matrix4f.transform(Util.toMatrix(getOrientation()), new Vector4f(amount.x, amount.y, amount.z, 0), null);
		Vector3f combined = (Vector3f) getRightDirection().scale(amount.x);
		Vector3f.add(combined, (Vector3f) getUpDirection().scale(amount.y), combined);
		Vector3f.add(combined, (Vector3f) getViewDirection().scale(-amount.z), combined); // We need the BACK direction here since that is our pos z!
//		moveInWorld(new Vector3f(temp.x, temp.y, temp.z));
		moveInWorld(combined);
		setDirty(true);
	}
	public void moveInWorld(Vector3f amount) {
		recalculateIfDirty();
		setPosition(Vector3f.add(position, amount, null));
	}

	public Vector3f localPositionToWorld(Vector3f localPosition) {
		return localToWorld(new Vector4f(localPosition.x, localPosition.y, localPosition.z, 1f));
	}

	public Vector3f localDirectionToWorld(Vector3f localAxis) {
		Vector4f temp = Matrix4f.transform((Matrix4f) Util.toMatrix(getOrientation().negate(null)), new Vector4f(localAxis.x,localAxis.y,localAxis.z, 0), null);
		return new Vector3f(temp.x, temp.y, temp.z);
//		return localToWorld(new Vector4f(localAxis.x, localAxis.y, localAxis.z, 0));
	}
	
	public Vector3f localToWorld(Vector4f homogenVector) {
		Vector4f temp = Matrix4f.transform((Matrix4f) getTranslationRotation().invert(), homogenVector, null);
//		Vector3f combined = (Vector3f) getRightDirection().scale(homogenVector.x);
//		Vector3f.add(combined, (Vector3f) getUpDirection().scale(homogenVector.y), combined);
//		Vector3f.add(combined, (Vector3f) getViewDirection().scale(homogenVector.z), combined);
//		return (Vector3f) new Vector3f(combined.x, combined.y, combined.z);
		return new Vector3f(temp.x, temp.y, temp.z);
	}

	public Matrix4f getTranslationRotation() {
		recalculateIfDirty();
		return translationRotation;
	}
	private Matrix4f calculateTranslationRotation() {
		Matrix4f temp = new Matrix4f();
		temp.setIdentity();
		Matrix4f.mul(temp, Util.toMatrix(orientation), temp); // TODO: SWITCH THESE LINES....
		Matrix4f.translate(position, temp, temp);
			if(parent != null) {
				parentMatrix = parent.getTranslationRotation();
			}
			if(parentMatrix == null) {
				parentMatrix = new Matrix4f();
				Matrix4f.setIdentity(parentMatrix);
			}
			temp = Matrix4f.mul(parentMatrix, temp, null);

		setDirty(false);
		return temp;
	}
	private Matrix4f calculateTransformation() {
		Matrix4f temp = new Matrix4f();
		temp.setIdentity();
		Matrix4f.translate(position, temp, temp);
		Matrix4f.mul(temp, Util.toMatrix(orientation), temp);
		temp.scale(scale);
			if(parent != null) {
				parentMatrix = parent.getTransformation();
			}
			if(parentMatrix == null) {
				parentMatrix = new Matrix4f();
				Matrix4f.setIdentity(parentMatrix);
			}
			temp = Matrix4f.mul(parentMatrix, temp, null);
                
		setDirty(false);
		return temp;
	}
	
	public Matrix4f getTransformation() {
		recalculateIfDirty();
		return transformation;
	}
	private void recalculateIfDirty() {
		if(isDirty() || translationRotation == null || transformation == null) {
			recalculate();
		}
	}
	public void recalculate() {
		translationRotation = calculateTranslationRotation();
		viewMatrix = new Matrix4f(translationRotation);
//		viewMatrix.invert();
		transformation = calculateTransformation();
			if(children == null) {
				children = new ArrayList<>();
			}
			for (Transform child : children) {
				child.recalculate();
			}
		bufferMatrixes();
		hasMoved = true;
	}
	
	public boolean isDirty() {
		return isDirty;
	}
	public void setDirty(boolean isDirty) {
            this.isDirty = isDirty;
	}

	public boolean isHasMoved() {
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
	private boolean equals(Quaternion a, Quaternion b) {
		return a.x == b.x && a.y == b.y && a.z == b.z && a.w == b.w;
	}

	public Matrix4f getViewMatrix() {
		return viewMatrix;
	}

	public void init(World world) {
		modelMatrixBuffer = BufferUtils.createFloatBuffer(16);
		modelMatrixBuffer.rewind();
		viewMatrixBuffer = BufferUtils.createFloatBuffer(16);
		viewMatrixBuffer.rewind();
		recalculate();
	}

	protected void bufferMatrixes() {
		synchronized(modelMatrixBuffer) {
			modelMatrixBuffer.rewind();
			transformation.store(modelMatrixBuffer);
			modelMatrixBuffer.rewind();
		}

		synchronized(viewMatrixBuffer) {
			viewMatrixBuffer.rewind();
			translationRotation.store(viewMatrixBuffer);
			viewMatrixBuffer.rewind();
		}
	}

	public FloatBuffer getTransformationBuffer() {
		return modelMatrixBuffer.asReadOnlyBuffer();
	}
	public FloatBuffer getTranslationRotationBuffer() {
		return viewMatrixBuffer.asReadOnlyBuffer();
	}

	private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
		stream.defaultReadObject();
		init(null);
	}
}