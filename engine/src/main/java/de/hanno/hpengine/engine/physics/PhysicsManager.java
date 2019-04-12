package de.hanno.hpengine.engine.physics;

import com.bulletphysics.collision.broadphase.BroadphaseInterface;
import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.collision.dispatch.CollisionConfiguration;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.collision.shapes.CollisionShape;
import com.bulletphysics.collision.shapes.SphereShape;
import com.bulletphysics.collision.shapes.StaticPlaneShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.DynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.ConstraintSolver;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import com.bulletphysics.linearmath.*;
import de.hanno.hpengine.engine.backend.BackendType;
import de.hanno.hpengine.engine.component.PhysicsComponent;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.graphics.renderer.Renderer;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.state.RenderSystem;
import de.hanno.hpengine.engine.manager.Manager;
import de.hanno.hpengine.engine.threads.TimeStepThread;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.transform.AABB;
import de.hanno.hpengine.util.commandqueue.CommandQueue;
import de.hanno.hpengine.util.commandqueue.FutureCallable;
import org.jetbrains.annotations.NotNull;

import javax.vecmath.Matrix4f;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class PhysicsManager implements Manager, RenderSystem {

    private final Renderer<? extends BackendType> renderer;
    private DynamicsWorld dynamicsWorld;
	private RigidBody ground;
    private final CommandQueue commandQueue = new CommandQueue();

    public PhysicsManager(Renderer renderer) {
		this(new Vector3f(0,-20,0), renderer);
	}
	public PhysicsManager(Vector3f gravity, Renderer renderer) {
        this.renderer = renderer;
        setupBullet(renderer, gravity);
        new TimeStepThread("Physics", 0.001f) {

            @Override
            public void update(float seconds) {
                try {
                    commandQueue.executeCommands();
                    dynamicsWorld.stepSimulation(seconds*1000);
                } catch (Exception e) {
                    System.out.println("e = " + e);
                    e.printStackTrace();
                }
            }
        }.start();
	}
	
	public PhysicsComponent addBallPhysicsComponent(Entity owner) {
		return addBallPhysicsComponent(owner, 1, 10);
	}
	public PhysicsComponent addBallPhysicsComponent(Entity owner, float radius, float mass) {
		SphereShape sphereShape = new SphereShape(radius);
		Vector3f inertia = new Vector3f();
		sphereShape.calculateLocalInertia(mass, inertia);
		return addPhysicsComponent(new MeshShapeInfo( () -> sphereShape, owner, mass, inertia));
	}
    public PhysicsComponent addBallPhysicsComponent(Entity owner, float radius) {
        return addBallPhysicsComponent(owner, radius, 10);
    }

	public PhysicsComponent addBoxPhysicsComponent(Entity entity) {
		return addBallPhysicsComponent(entity, 10);
	}
	public PhysicsComponent addBoxPhysicsComponent(Entity owner, float mass) {
		AABB minMax = owner.getMinMaxWorld();
		Vector3f halfExtends = new Vector3f(minMax.getMax().x - minMax.getMin().x, minMax.getMax().y - minMax.getMin().y, minMax.getMax().z - minMax.getMin().z);
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
		return addPhysicsComponent(new MeshShapeInfo( () -> boxShape, owner, mass, inertia));
	}

	public PhysicsComponent addHullPhysicsComponent(Entity owner, float mass) {
        throw new IllegalStateException("Currently not implemented!");
//		ObjectArrayList<Vector3f> list = new ObjectArrayList<>();
//		ModelComponent modelComponent = owner.getComponent(ModelComponent.class);
//
//		float[] vertices = modelComponent.getPositions();
//		for (int i = 0; i < vertices.length; i += 3) {
//			list.add(new Vector3f(vertices[i], vertices[i+1], vertices[i+2]));
//		}
//
//		CollisionShape shape = new ConvexHullShape(list);
//		Vector3f inertia = new Vector3f();
//		shape.calculateLocalInertia(1f, inertia);
//		return addPhysicsComponent(new MeshShapeInfo( () -> shape, owner, mass, inertia));
	}
	
	public PhysicsComponent addMeshPhysicsComponent(Entity owner, float mass) {
        Vector3f inertia = new Vector3f();
        Supplier<CollisionShape> collisionShapeSupplier = () -> supplyCollisionShape(owner, mass, inertia);

        MeshShapeInfo info = new MeshShapeInfo(collisionShapeSupplier, owner, mass, inertia);
        return addPhysicsComponent(info);
	}

    public CollisionShape supplyCollisionShape(Entity owner, float mass, Vector3f inertia) {
        throw new IllegalStateException("Currently not implemented!");
//        ModelComponent modelComponent = owner.getComponent(ModelComponent.class);
//        if(modelComponent == null || !modelComponent.isInitialized()) {
//            throw new IllegalStateException("ModelComponent null or not initialized");
//        }
//
//        float[] vertices = modelComponent.getPositions();
//        int[] indices = modelComponent.getIndices();
//        ByteBuffer vertexBuffer = BufferUtils.createByteBuffer(vertices.length * 4);
//        ByteBuffer indexBuffer = BufferUtils.createByteBuffer(indices.length * 4);
//
//        for (int i = 0; i < vertices.length; i+=3) {
//            org.joml.Vector3f vec = new org.joml.Vector3f(vertices[i], vertices[i+1], vertices[i+2]);
//            org.joml.Vector3f scaledVec = org.joml.Vector3f.cross(vec, owner.getScale(), null);
//
//            vertexBuffer.putFloat(scaledVec.x);
//            vertexBuffer.putFloat(scaledVec.y);
//            vertexBuffer.putFloat(scaledVec.z);
//        }
//        for (int i = 0; i < indices.length; i+=3) {
//            indexBuffer.putFloat(indices[i]);
//            indexBuffer.putFloat(indices[i+1]);
//            indexBuffer.putFloat(indices[i+2]);
//        }
//
//        vertexBuffer.rewind();
//        indexBuffer.rewind();
//
//        TriangleIndexVertexArray vertexArray = new TriangleIndexVertexArray(indices.length/3, indexBuffer, 0, vertices.length,vertexBuffer, 0);
//        BvhTriangleMeshShape shape = new BvhTriangleMeshShape(vertexArray, true);
//        shape.calculateLocalInertia(mass, inertia);
//        return shape;
    }

    public CommandQueue getCommandQueue() {
        return commandQueue;
    }

    public void debugDrawWorld() {
        getDynamicsWorld().debugDrawWorld();
    }

    @Override
    public void update(float deltaSeconds) {

    }

    @Override
    public void clear() {

    }

    @Override
    public void onEntityAdded(@NotNull List<? extends Entity> entities) {

    }

    @Override
    public void afterUpdate(float deltaSeconds) {

    }

    @Override
    public void render(@NotNull DrawResult result, @NotNull RenderState state) {
        if (Config.getInstance().isDrawLines()) {
            renderer.drawAllLines((program) -> {
                program.setUniform("diffuseColor", new org.joml.Vector3f(1,1,0));
                debugDrawWorld();
            });
        }
    }

    public class MeshShapeInfo {
        public MeshShapeInfo(Supplier<CollisionShape> shapeSupplier, Entity owner, float mass, Vector3f inertia) {
            this.shapeSupplier = shapeSupplier;
            this.owner = owner;
            this.mass = mass;
            this.inertia = inertia;
        }

        public Supplier<CollisionShape> shapeSupplier;
        public Entity owner;
        public float mass;
        public Vector3f inertia;
    }
	
	public PhysicsComponent addPhysicsComponent(MeshShapeInfo info) {
		return new PhysicsComponent(info.owner, info, this);
	}
	

	private void setupBullet(Renderer renderer, Vector3f gravity) {
		BroadphaseInterface broadphase = new DbvtBroadphase();
		CollisionConfiguration collisionConfiguration = new DefaultCollisionConfiguration();
		CollisionDispatcher dispatcher = new CollisionDispatcher(collisionConfiguration);
		ConstraintSolver constraintSolver = new SequentialImpulseConstraintSolver();
		dynamicsWorld = new DiscreteDynamicsWorld(dispatcher, broadphase, constraintSolver, collisionConfiguration) {
            @Override
            public void debugDrawObject(Transform worldTransform, CollisionShape shape, Vector3f color) {
                super.debugDrawObject(worldTransform, shape, color);
                Vector3f from = new Vector3f();
                Vector3f to = new Vector3f();
                shape.getAabb(worldTransform, from, to);
                from.negate();
                getDebugDrawer().drawAabb(from, to, color);
            }
        };
		dynamicsWorld.setGravity(gravity);
		dynamicsWorld.setDebugDrawer(new IDebugDraw() {
            Logger logger = Logger.getLogger("Physics Factory Debug Draw");
			
			@Override
			public void setDebugMode(int arg0) {
			}
			
			@Override
			public void reportErrorWarning(String arg0) {
                logger.info(arg0);
			}
			
			@Override
			public int getDebugMode() {
                int flags =
                        DebugDrawModes.DRAW_AABB
//                        & DebugDrawModes.DRAW_WIREFRAME
//                            DebugDrawModes.DRAW_TEXT
//                        & DebugDrawModes.DRAW_CONTACT_POINTS
//                         DebugDrawModes.MAX_DEBUG_DRAW_MODE
                        ;
                return Config.getInstance().isDrawLines() ? flags : 0;
			}
			
			@Override
			public void drawLine(Vector3f start, Vector3f end, Vector3f color) {
                renderer.batchLine(
						new org.joml.Vector3f(start.x, start.y, start.z),
						new org.joml.Vector3f(end.x, end.y, end.z));
			}

            @Override
            public void drawAabb(Vector3f from, Vector3f to, Vector3f color) {

                drawLine(from, new Vector3f(to.x, from.y, from.z), color);
                drawLine(from, new Vector3f(from.x, to.y, from.z), color);
                drawLine(from, new Vector3f(to.x, to.y, from.z), color);

                drawLine(new Vector3f(from.x, to.y, to.z), to, color);
                drawLine(new Vector3f(to.x, from.y, to.z), to, color);
                drawLine(new Vector3f(from.x, from.y, to.z), to, color);

                drawLine(from, to, color);
            }

			@Override
			public void drawContactPoint(Vector3f arg0, Vector3f arg1, float arg2,
					int arg3, Vector3f arg4) {
			}
			
			@Override
			public void draw3dText(Vector3f arg0, String arg1) {
                logger.info(arg0.toString() + " - " + arg1);
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
	
	private DynamicsWorld getDynamicsWorld() {
		return dynamicsWorld;
	}

    List<RigidBody> rigidBodyCache = new ArrayList<>();
    public void registerRigidBody(RigidBody rigidBody) {
        rigidBodyCache.add(rigidBody);
        dynamicsWorld.addRigidBody(rigidBody);
    }

    public void clearWorld() {
        for(RigidBody rigidBody : rigidBodyCache) {
            unregisterRigidBody(rigidBody);
        }
    }

    public CompletableFuture<Object> unregisterRigidBody(final RigidBody rigidBody) {
        return getCommandQueue().addCommand(new FutureCallable<Object>() {
            @Override
            public Object execute() throws Exception {
                getDynamicsWorld().removeRigidBody(rigidBody);
                return null;
            }
        });
    }

    public RigidBody getGround() {
		return ground;
	}
	public void setGround(RigidBody ground) {
		this.ground = ground;
	}
}
