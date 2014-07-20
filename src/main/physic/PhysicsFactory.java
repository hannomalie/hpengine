package main.physic;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.vecmath.Matrix4f;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import main.World;
import main.component.PhysicsComponent;
import main.model.DataChannels;
import main.model.IEntity;
import main.util.Util;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector4f;

import com.bulletphysics.collision.broadphase.BroadphaseInterface;
import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.collision.dispatch.CollisionConfiguration;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.collision.shapes.CollisionShape;
import com.bulletphysics.collision.shapes.ConvexHullShape;
import com.bulletphysics.collision.shapes.SphereShape;
import com.bulletphysics.collision.shapes.StaticPlaneShape;
import com.bulletphysics.collision.shapes.TriangleIndexVertexArray;
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

public class PhysicsFactory {
	
	private World world;
	private DynamicsWorld dynamicsWorld;

	public PhysicsFactory(World world) {
		this(world, new Vector3f(0,-20,0));
	}
	public PhysicsFactory(World world, Vector3f gravity) {
		this.world = world;
		setupBullet(gravity);
	}
	
	public void update(float seconds) {
		dynamicsWorld.stepSimulation(seconds);
	}

	public PhysicsComponent addBallPhysicsComponent(IEntity owner) {
		return addBallPhysicsComponent(owner, 1, 10);
	}
	public PhysicsComponent addBallPhysicsComponent(IEntity owner, float radius) {
		return addBallPhysicsComponent(owner, radius, 10);
	}
	public PhysicsComponent addBallPhysicsComponent(IEntity owner, float radius, float mass) {
		SphereShape sphereShape = new SphereShape(radius);
		Vector3f inertia = new Vector3f();
		sphereShape.calculateLocalInertia(mass, inertia);
		return addPhysicsComponent(sphereShape, owner, mass, inertia);
	}

	public PhysicsComponent addBoxPhysicsComponent(IEntity entity) {
		return addBallPhysicsComponent(entity, 10);
	}
	public PhysicsComponent addBoxPhysicsComponent(IEntity owner, float mass) {
		Vector4f[] minMax = owner.getMinMaxWorld();
		Vector3f halfExtends = new Vector3f(minMax[1].x - minMax[0].x, minMax[1].y - minMax[0].y, minMax[1].z - minMax[0].z);
		halfExtends.scale(0.5f);
		return addBoxPhysicsComponent(owner, halfExtends, mass);
	}
	public PhysicsComponent addBoxPhysicsComponent(IEntity owner, float halfExtends, float mass) {
		return addBoxPhysicsComponent(owner, new Vector3f(halfExtends, halfExtends, halfExtends), mass);
	}
	public PhysicsComponent addBoxPhysicsComponent(IEntity owner, Vector3f halfExtends, float mass) {
		BoxShape boxShape = new BoxShape(halfExtends);
		Vector3f inertia = new Vector3f();
		boxShape.calculateLocalInertia(1f, inertia);
		return addPhysicsComponent(boxShape, owner, mass, inertia);
	}

	public PhysicsComponent addHullPhysicsComponent(IEntity owner, float mass) {
		ObjectArrayList<Vector3f> list = new ObjectArrayList<>();
		float[] vertices = owner.getVertexBuffer().getValues(DataChannels.POSITION3);
		for (int i = 0; i < vertices.length; i += 3) {
			list.add(new Vector3f(vertices[i], vertices[i+1], vertices[i+2]));
		}
		
		CollisionShape shape = new ConvexHullShape(list);
		Vector3f inertia = new Vector3f();
		shape.calculateLocalInertia(1f, inertia);
		return addPhysicsComponent(shape, owner, mass, inertia);
	}
	
	public PhysicsComponent addMeshPhysicsComponent(IEntity owner, float mass) {
		
		float[] vertices = owner.getVertexBuffer().getValues(DataChannels.POSITION3);
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
	
	public PhysicsComponent addPhysicsComponent(CollisionShape shape, IEntity owner, float mass, Vector3f inertia) {
		Transform transform = Util.toBullet(owner.getTransform());
		MotionState motionState = new DefaultMotionState(transform);
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
				if(World.DRAWLINES_ENABLED) {
					return 1;
				} else { return 0; }
			}
			
			@Override
			public void drawLine(Vector3f arg0, Vector3f arg1, Vector3f arg2) {
				world.getRenderer().drawLine(
						new org.lwjgl.util.vector.Vector3f(arg0.x,  arg0.y, arg0.z),
						new org.lwjgl.util.vector.Vector3f(arg1.x,  arg1.y, arg1.z));
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
		RigidBody ground = new RigidBody(groundBodyConstructionInfo);
		dynamicsWorld.addRigidBody(ground);
	}
	
	public DynamicsWorld getDynamicsWorld() {
		return dynamicsWorld;
	}
}
