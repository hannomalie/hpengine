package component;

import javax.vecmath.Matrix4f;

import engine.model.Entity;
import util.Util;

import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.linearmath.Transform;

public class PhysicsComponent extends BaseComponent {
	
	public enum MotionType {
		STATIC,
		DYNAMIC
	}
	
	RigidBody rigidBody;
	private Entity owner;
	
	public PhysicsComponent(Entity owner, RigidBody rigidBody) {
		this.owner = owner;
		this.rigidBody = rigidBody;
		owner.getComponents().put(PhysicsComponent.class, this);
	}
	
	public void update(float seconds) {
		Transform out = new Transform();
		rigidBody.getWorldTransform(out);
		Matrix4f temp = new Matrix4f();
		out.getMatrix(temp);
		engine.Transform converted = Util.fromBullet(out);
//		System.out.println("Rotation " + converted.getOrientation().x + " " + converted.getOrientation().y + " " + converted.getOrientation().z + " " + converted.getOrientation().w);
		owner.getTransform().setScale(owner.getScale());

		owner.getTransform().setOrientation(converted.getOrientation());
		//owner.getTransform().setPosition(new org.lwjgl.util.vector.Vector3f(out.origin.x,out.origin.y,out.origin.z));
		owner.getTransform().setPosition(converted.getPosition());
//		System.out.println("Rotation own " + owner.getOrientation().x + " " + owner.getOrientation().y + " " + owner.getOrientation().z + " " + owner.getOrientation().w);
	}

	@Override
	public Class getIdentifier() {
		return PhysicsComponent.class;
	}

	public RigidBody getRigidBody() {
		return rigidBody;
	}
}
