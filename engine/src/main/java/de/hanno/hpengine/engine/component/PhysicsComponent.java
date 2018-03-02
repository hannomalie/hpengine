package de.hanno.hpengine.engine.component;

import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.MotionState;
import de.hanno.hpengine.engine.transform.Transform;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.physics.PhysicsManager;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.commandqueue.FutureCallable;

import javax.vecmath.Matrix4f;
import java.util.concurrent.ExecutionException;

public class PhysicsComponent extends BaseComponent {
    public static final String COMPONENT_KEY = PhysicsComponent.class.getSimpleName();
    private transient com.bulletphysics.linearmath.Transform initialTransform;

    RigidBodyConstructionInfo rigidBodyConstructionInfo;
	private Entity owner;
    private transient RigidBody rigidBody;
    private transient PhysicsManager.MeshShapeInfo info;

	public PhysicsComponent(Entity owner, PhysicsManager.MeshShapeInfo meshShapeInfo) {
		this.owner = owner;
		this.info = meshShapeInfo;
		owner.addComponent(this);
	}

    @Override
    public void init(Engine engine) {
        initialTransform = Util.toBullet(owner);
        actuallyCreatePhysicsObject(engine);
    }

    private void actuallyCreatePhysicsObject(Engine engine) {
        MotionState motionState = new DefaultMotionState(initialTransform);
        rigidBodyConstructionInfo = new RigidBodyConstructionInfo(info.mass, motionState, info.shapeSupplier.get(), info.inertia);
        rigidBodyConstructionInfo.restitution = 0.5f;
        rigidBody = new RigidBody(rigidBodyConstructionInfo);
        rigidBody.setUserPointer(owner);
        registerRigidBody(engine);
    }

    private void registerRigidBody(Engine engine) {
        try {
            engine.getPhysicsManager().getCommandQueue().addCommand(new FutureCallable<Object>() {
                @Override
                public Object execute() throws Exception {
                    engine.getPhysicsManager().registerRigidBody(rigidBody);
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
		com.bulletphysics.linearmath.Transform out = new com.bulletphysics.linearmath.Transform();
		rigidBody.getWorldTransform(out);
		Matrix4f temp = new Matrix4f();
		out.getMatrix(temp);
		Transform converted = Util.fromBullet(out);
        owner.set(converted);
    }

    public void reset(Engine engine) {
        engine.getPhysicsManager().unregisterRigidBody(rigidBody);
        actuallyCreatePhysicsObject(engine);
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
        return rigidBody.getInvMass() == Float.POSITIVE_INFINITY;
    }
}
