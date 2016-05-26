package physic;

import com.bulletphysics.collision.broadphase.BroadphaseInterface;
import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.collision.dispatch.CollisionConfiguration;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.shapes.*;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.DynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.ConstraintSolver;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.IDebugDraw;
import com.bulletphysics.linearmath.MotionState;
import com.bulletphysics.linearmath.Transform;
import com.bulletphysics.util.ObjectArrayList;
import component.ModelComponent;
import component.PhysicsComponent;
import config.Config;
import engine.AppContext;
import engine.TimeStepThread;
import engine.model.DataChannels;
import engine.model.Entity;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector4f;
import renderer.Renderer;
import util.Util;

import javax.vecmath.Matrix4f;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class PhysicsFactory {
	
	private AppContext appContext;
	private DynamicsWorld dynamicsWorld;
	private RigidBody ground;

	public PhysicsFactory(AppContext appContext) {
		this(appContext, new Vector3f(0,-20,0));
	}
	public PhysicsFactory(AppContext appContext, Vector3f gravity) {
		this.appContext = appContext;
		setupBullet(gravity);
        new TimeStepThread("Physics", 0) {

            @Override
            public void update(float seconds) {
                dynamicsWorld.stepSimulation(seconds*1000);
            }
        }.start();
	}
	
	public PhysicsComponent addBallPhysicsComponent(Entity owner) {
		return addBallPhysicsComponent(owner, 1, 10);
	}
	public PhysicsComponent addBallPhysicsComponent(Entity owner, float radius) {
		return addBallPhysicsComponent(owner, radius, 10);
	}
	public PhysicsComponent addBallPhysicsComponent(Entity owner, float radius, float mass) {
		SphereShape sphereShape = new SphereShape(radius);
		Vector3f inertia = new Vector3f();
		sphereShape.calculateLocalInertia(mass, inertia);
		return addPhysicsComponent(sphereShape, owner, mass, inertia);
	}

	public PhysicsComponent addBoxPhysicsComponent(Entity entity) {
		return addBallPhysicsComponent(entity, 10);
	}
	public PhysicsComponent addBoxPhysicsComponent(Entity owner, float mass) {
		Vector4f[] minMax = owner.getMinMaxWorld();
		Vector3f halfExtends = new Vector3f(minMax[1].x - minMax[0].x, minMax[1].y - minMax[0].y, minMax[1].z - minMax[0].z);
		halfExtends.scale(0.5f);
		return addBoxPhysicsComponent(owner, halfExtends, mass);
	}
	public PhysicsComponent addBoxPhysicsComponent(Entity owner, float halfExtends, float mass) {
		return addBoxPhysicsComponent(owner, new Vector3f(halfExtends, halfExtends, halfExtends), mass);
	}
	public PhysicsComponent addBoxPhysicsComponent(Entity owner, Vector3f halfExtends, float mass) {
		BoxShape boxShape = new BoxShape(halfExtends);
		Vector3f inertia = new Vector3f();
		boxShape.calculateLocalInertia(1f, inertia);
		return addPhysicsComponent(boxShape, owner, mass, inertia);
	}

	public PhysicsComponent addHullPhysicsComponent(Entity owner, float mass) {
		ObjectArrayList<Vector3f> list = new ObjectArrayList<>();
		ModelComponent modelComponent = owner.getComponent(ModelComponent.class);

		float[] vertices = modelComponent.getVertexBuffer().getValues(DataChannels.POSITION3);
		for (int i = 0; i < vertices.length; i += 3) {
			list.add(new Vector3f(vertices[i], vertices[i+1], vertices[i+2]));
		}
		
		CollisionShape shape = new ConvexHullShape(list);
		Vector3f inertia = new Vector3f();
		shape.calculateLocalInertia(1f, inertia);
		return addPhysicsComponent(shape, owner, mass, inertia);
	}
	
	public PhysicsComponent addMeshPhysicsComponent(Entity owner, float mass) {
		ModelComponent modelComponent = owner.getComponent(ModelComponent.class);

		float[] vertices = modelComponent.getVertexBuffer().getValues(DataChannels.POSITION3);
		ByteBuffer vertexBuffer = BufferUtils.createByteBuffer(vertices.length * 8);
		ByteBuffer indexBuffer = BufferUtils.createByteBuffer(vertices.length * 8);
		
		List<Vector3f> scaledVertices = new ArrayList<>(vertices.length/3);
		for (int i = 0; i < vertices.length; i+=3) {
			org.lwjgl.util.vector.Vector3f vec = new org.lwjgl.util.vector.Vector3f(vertices[i], vertices[i+1], vertices[i+2]);
			org.lwjgl.util.vector.Vector3f scaledVec = org.lwjgl.util.vector.Vector3f.cross(vec, owner.getScale(), null);

			vertexBuffer.putFloat(scaledVec.x);
			vertexBuffer.putFloat(scaledVec.y);
			vertexBuffer.putFloat(scaledVec.z);
			indexBuffer.putFloat(i);
			indexBuffer.putFloat(i+1);
			indexBuffer.putFloat(i+2);
		}
		
		vertexBuffer.rewind();
		indexBuffer.rewind();

		TriangleIndexVertexArray vertexArray = new TriangleIndexVertexArray(vertices.length/3, indexBuffer, 0, vertices.length,vertexBuffer, 0); 
		CollisionShape shape = new com.bulletphysics.collision.shapes.BvhTriangleMeshShape(vertexArray, true);
		Vector3f inertia = new Vector3f();
		shape.calculateLocalInertia(mass, inertia);
		return addPhysicsComponent(shape, owner, mass, inertia);
	}
	
	public PhysicsComponent addPhysicsComponent(CollisionShape shape, Entity owner, float mass, Vector3f inertia) {
		Transform transform = Util.toBullet(owner.getTransform());
		MotionState motionState = new DefaultMotionState(transform);
//		System.out.println("bullet: " + transform.origin);
//		System.out.println("own: " + owner.getTransform().getPosition());
//		Quat4f out = new Quat4f();
//		System.out.println("bullet: " + transform.getRotation(out));
//		System.out.println("own: " + owner.getTransform().getOrientation());
		RigidBodyConstructionInfo bodyConstructionInfo = new RigidBodyConstructionInfo(mass, motionState, shape, inertia);
		bodyConstructionInfo.restitution = 0.5f;
		RigidBody body = new RigidBody(bodyConstructionInfo);
		dynamicsWorld.addRigidBody(body);
		return new PhysicsComponent(owner, body);
	}
	

	private void setupBullet(Vector3f gravity) {
		BroadphaseInterface broadphase = new DbvtBroadphase();
		CollisionConfiguration collisionConfiguration = new DefaultCollisionConfiguration();
		CollisionDispatcher dispatcher = new CollisionDispatcher(collisionConfiguration);
		ConstraintSolver constraintSolver = new SequentialImpulseConstraintSolver();
		dynamicsWorld = new DiscreteDynamicsWorld(dispatcher, broadphase, constraintSolver, collisionConfiguration);
		dynamicsWorld.setGravity(gravity);
		dynamicsWorld.setDebugDrawer(new IDebugDraw() {
			
			@Override
			public void setDebugMode(int arg0) {
			}
			
			@Override
			public void reportErrorWarning(String arg0) {
			}
			
			@Override
			public int getDebugMode() {
				if(Config.DRAWLINES_ENABLED) {
					return 1;
				} else { return 0; }
			}
			
			@Override
			public void drawLine(Vector3f arg0, Vector3f arg1, Vector3f arg2) {
                Renderer.getInstance().batchLine(
						new org.lwjgl.util.vector.Vector3f(arg0.x, arg0.y, arg0.z),
						new org.lwjgl.util.vector.Vector3f(arg1.x, arg1.y, arg1.z));
			}
			
			@Override
			public void drawContactPoint(Vector3f arg0, Vector3f arg1, float arg2,
					int arg3, Vector3f arg4) {
			}
			
			@Override
			public void draw3dText(Vector3f arg0, String arg1) {
				
			}
		});
		
		CollisionShape groundShape = new StaticPlaneShape(new javax.vecmath.Vector3f(0,1,0), 0.25f);
		Vector3f inertia = new Vector3f();
		groundShape.calculateLocalInertia(0, inertia);
		Transform transform = new Transform(new Matrix4f(new Quat4f(), new Vector3f(0,-40,0), 1f));
		MotionState groundMotionState = new DefaultMotionState(transform);
		RigidBodyConstructionInfo groundBodyConstructionInfo = new RigidBodyConstructionInfo(0, groundMotionState, groundShape, inertia);
		groundBodyConstructionInfo.restitution = 0.25f;
		ground = new RigidBody(groundBodyConstructionInfo);
		dynamicsWorld.addRigidBody(ground);
	}
	
	public DynamicsWorld getDynamicsWorld() {
		return dynamicsWorld;
	}

	public RigidBody getGround() {
		return ground;
	}
	public void setGround(RigidBody ground) {
		this.ground = ground;
	}
}
