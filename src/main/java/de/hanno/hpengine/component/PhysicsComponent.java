package de.hanno.hpengine.component;

import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.MotionState;
import com.bulletphysics.linearmath.Transform;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.physic.PhysicsFactory;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.commandqueue.FutureCallable;

import javax.vecmath.Matrix4f;
import java.util.concurrent.ExecutionException;

public class PhysicsComponent extends BaseComponent {
    private transient Transform initialTransform;

    RigidBodyConstructionInfo rigidBodyConstructionInfo;
	private Entity owner;
    private transient RigidBody rigidBody;
    private transient PhysicsFactory.MeshShapeInfo info;

	public PhysicsComponent(Entity owner, PhysicsFactory.MeshShapeInfo meshShapeInfo) {
		this.owner = owner;
		this.info = meshShapeInfo;
		owner.addComponent(this);
	}

    @Override
    public void init() {
        initialTransform = Util.toBullet(owner.getTransform());
        actuallyCreatePhysicsObject();

        initialized = true;
    }

    private void actuallyCreatePhysicsObject() {
        MotionState motionState = new DefaultMotionState(initialTransform);
        rigidBodyConstructionInfo = new RigidBodyConstructionInfo(info.mass, motionState, info.shapeSupplier.get(), info.inertia);
        rigidBodyConstructionInfo.restitution = 0.5f;
        rigidBody = new RigidBody(rigidBodyConstructionInfo);
        rigidBody.setUserPointer(owner);
        registerRigidBody();
    }

    private void registerRigidBody() {
        try {
            Engine.getInstance().getPhysicsFactory().getCommandQueue().addCommand(new FutureCallable<Object>() {
                @Override
                public Object execute() throws Exception {
                    Engine.getInstance().getPhysicsFactory().registerRigidBody(rigidBody);
                    return null;
                }
            }).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public void update(float seconds) {
		Transform out = new Transform();
		rigidBody.getWorldTransform(out);
		Matrix4f temp = new Matrix4f();
		out.getMatrix(temp);
		de.hanno.hpengine.engine.Transform converted = Util.fromBullet(out);
		owner.getTransform().setScale(owner.getScale());

		owner.getTransform().setOrientation(converted.getOrientation());
		owner.getTransform().setPosition(converted.getPosition());
	}

    public void reset() {
        if(isInitialized()) {
            Engine.getInstance().getPhysicsFactory().unregisterRigidBody(rigidBody);
            actuallyCreatePhysicsObject();
        }
    }

	@Override
	public String getIdentifier() {
		return "PhysicsComponent";
	}

	public RigidBody getRigidBody() {
		return rigidBody;
	}

    public boolean isDynamic() {
        return !isStatic();
    }

    private boolean isStatic() {
        if(isInitialized()) {
            return rigidBody.getInvMass() == Float.POSITIVE_INFINITY;
        }
        return true;
    }
}
