package main.component;

import javax.vecmath.Matrix4f;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector4f;

import main.model.IEntity;
import main.util.Util;

import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.linearmath.Transform;

public class PhysicsComponent implements IGameComponent {
	
	public enum MotionType {
		STATIC,
		DYNAMIC
	}
	
	RigidBody rigidBody;
	private IEntity owner;
	
	public PhysicsComponent(IEntity owner, RigidBody rigidBody) {
		this.owner = owner;
		this.rigidBody = rigidBody;
		owner.getComponents().put(ComponentIdentifier.Physic, this);
	}
	
	public void update(float seconds) {
		Transform out = new Transform();
		rigidBody.getWorldTransform(out);
		Matrix4f temp = new Matrix4f();
		out.getMatrix(temp);
		main.Transform converted = Util.fromBullet(out);
//		System.out.println("Rotation " + converted.getOrientation().x + " " + converted.getOrientation().y + " " + converted.getOrientation().z + " " + converted.getOrientation().w);
		owner.getTransform().setScale(owner.getScale());

		owner.getTransform().setOrientation(converted.getOrientation());
		//owner.getTransform().setPosition(new org.lwjgl.util.vector.Vector3f(out.origin.x,out.origin.y,out.origin.z));
		owner.getTransform().setPosition(converted.getPosition());
//		System.out.println("Rotation own " + owner.getOrientation().x + " " + owner.getOrientation().y + " " + owner.getOrientation().z + " " + owner.getOrientation().w);
	}

	public RigidBody getRigidBody() {
		return rigidBody;
	}
}
