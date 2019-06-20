package de.hanno.hpengine.engine.component;

import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.MotionState;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.transform.Transform;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.physics.PhysicsManager;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.commandqueue.FutureCallable;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;

import javax.vecmath.Matrix4f;
import java.util.concurrent.ExecutionException;

public class PhysicsComponent extends BaseComponent {
    public static final String COMPONENT_KEY = PhysicsComponent.class.getSimpleName();
    private transient com.bulletphysics.linearmath.Transform initialTransform;

    RigidBodyConstructionInfo rigidBodyConstructionInfo;
	private Entity owner;
    private transient RigidBody rigidBody;
    private transient PhysicsManager.MeshShapeInfo info;
    private final PhysicsManager physicsManager;

    public PhysicsComponent(Entity owner, PhysicsManager.MeshShapeInfo meshShapeInfo, PhysicsManager physicsManager) {
        super(owner);
        this.owner = owner;
		this.info = meshShapeInfo;
        this.physicsManager = physicsManager;
        owner.addComponent(this);
        initialTransform = Util.toBullet(owner);
        actuallyCreatePhysicsObject(); // This probably has to be moved to physsicsmanaqger/physicssystem
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
            physicsManager.getCommandQueue().addCommand(new FutureCallable<Object>() {
                @Override
                public Object execute() throws Exception {
                    physicsManager.registerRigidBody(rigidBody);
                    return null;
                }
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    public void update(@NotNull CoroutineScope scope, float deltaSeconds) {
		com.bulletphysics.linearmath.Transform out = new com.bulletphysics.linearmath.Transform();
		rigidBody.getWorldTransform(out);
		Matrix4f temp = new Matrix4f();
		out.getMatrix(temp);
		Transform converted = Util.fromBullet(out);
        owner.set(converted);
    }

    public void reset(Engine engine) {
        engine.getPhysicsManager().unregisterRigidBody(rigidBody);
        actuallyCreatePhysicsObject();
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
