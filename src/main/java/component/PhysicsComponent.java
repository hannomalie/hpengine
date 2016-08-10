package component;

import javax.vecmath.Matrix4f;

import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import engine.AppContext;
import engine.model.Entity;
import util.Util;

import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.linearmath.Transform;

public class PhysicsComponent extends BaseComponent {
    public enum MotionType {
		STATIC,
		DYNAMIC
	}
	
	RigidBodyConstructionInfo rigidBodyConstructionInfo;
	private Entity owner;
    private transient RigidBody rigidBody;

	public PhysicsComponent(Entity owner, RigidBodyConstructionInfo rigidBodyConstructionInfo) {
		this.owner = owner;
		this.rigidBodyConstructionInfo = rigidBodyConstructionInfo;
		owner.getComponents().put(getIdentifier(), this);
	}

    @Override
    public void init() {
        rigidBody = new RigidBody(rigidBodyConstructionInfo);
        AppContext.getInstance().getPhysicsFactory().getDynamicsWorld().addRigidBody(rigidBody);
    }
	
	public void update(float seconds) {
		Transform out = new Transform();
		rigidBody.getWorldTransform(out);
		Matrix4f temp = new Matrix4f();
		out.getMatrix(temp);
		engine.Transform converted = Util.fromBullet(out);
		owner.getTransform().setScale(owner.getScale());

		owner.getTransform().setOrientation(converted.getOrientation());
		owner.getTransform().setPosition(converted.getPosition());
	}

	@Override
	public String getIdentifier() {
		return "PhysicsComponent";
	}

	public RigidBody getRigidBody() {
		return rigidBody;
	}
}
