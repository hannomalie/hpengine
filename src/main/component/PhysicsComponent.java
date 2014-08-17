package main.component;

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
		rigidBody.getMotionState().getWorldTransform(out);
		main.Transform converted = new main.Transform();//Util.fromBullet(out);
		Quat4f outQuat = new Quat4f();
		out.getRotation(outQuat);
//		System.out.println("Rotation " + outQuat.x + " " + outQuat.y + " " + outQuat.z + " " + outQuat.w);
//		owner.getTransform().setOrientation(new Quaternion(outQuat.x,outQuat.y,outQuat.z,outQuat.w));
		owner.getTransform().setScale(owner.getScale());
//		converted.setOrientation(new Quaternion(outQuat.x, outQuat.y, outQuat.z, outQuat.w));
		owner.getTransform().setPosition(new org.lwjgl.util.vector.Vector3f(out.origin.x,out.origin.y,out.origin.z));
	}

	public RigidBody getRigidBody() {
		return rigidBody;
	}
}
