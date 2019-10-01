package de.hanno.hpengine.engine.physics

import com.bulletphysics.collision.broadphase.BroadphaseInterface
import com.bulletphysics.collision.broadphase.DbvtBroadphase
import com.bulletphysics.collision.dispatch.CollisionConfiguration
import com.bulletphysics.collision.dispatch.CollisionDispatcher
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration
import com.bulletphysics.collision.shapes.BoxShape
import com.bulletphysics.collision.shapes.CollisionShape
import com.bulletphysics.collision.shapes.SphereShape
import com.bulletphysics.collision.shapes.StaticPlaneShape
import com.bulletphysics.dynamics.DiscreteDynamicsWorld
import com.bulletphysics.dynamics.DynamicsWorld
import com.bulletphysics.dynamics.RigidBody
import com.bulletphysics.dynamics.RigidBodyConstructionInfo
import com.bulletphysics.dynamics.constraintsolver.ConstraintSolver
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver
import com.bulletphysics.linearmath.DebugDrawModes
import com.bulletphysics.linearmath.DefaultMotionState
import com.bulletphysics.linearmath.IDebugDraw
import com.bulletphysics.linearmath.MotionState
import com.bulletphysics.linearmath.Transform
import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.component.PhysicsComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.renderer.Renderer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.threads.TimeStepThread
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.util.commandqueue.CommandQueue
import de.hanno.hpengine.util.commandqueue.FutureCallable
import kotlinx.coroutines.CoroutineScope

import javax.vecmath.Matrix4f
import javax.vecmath.Quat4f
import javax.vecmath.Vector3f
import java.util.ArrayList
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import java.util.logging.Logger

class PhysicsManager(gravity: Vector3f, renderer: Renderer<*>, private val config: Config) : Manager, RenderSystem {

    private val renderer: Renderer<out BackendType>
    private var dynamicsWorld: DynamicsWorld? = null
    var ground: RigidBody? = null
    val commandQueue = CommandQueue()

    internal var rigidBodyCache: MutableList<RigidBody> = ArrayList()

    constructor(renderer: Renderer<*>, config: Config) : this(Vector3f(0f, -20f, 0f), renderer, config) {}

    init {
        this.renderer = renderer
        setupBullet(renderer, gravity)
        object : TimeStepThread("Physics", 0.001f) {

            override fun update(seconds: Float) {
                try {
                    commandQueue.executeCommands()
                    dynamicsWorld!!.stepSimulation(seconds * 1000)
                } catch (e: Exception) {
                    println("e = $e")
                    e.printStackTrace()
                }

            }
        }.start()
    }

    @JvmOverloads
    fun addBallPhysicsComponent(owner: Entity, radius: Float = 1f, mass: Float = 10f): PhysicsComponent {
        val sphereShape = SphereShape(radius)
        val inertia = Vector3f()
        sphereShape.calculateLocalInertia(mass, inertia)
        return addPhysicsComponent(MeshShapeInfo({ sphereShape }, owner, mass, inertia))
    }

    fun addBoxPhysicsComponent(entity: Entity): PhysicsComponent {
        return addBallPhysicsComponent(entity, 10f)
    }

    fun addBoxPhysicsComponent(owner: Entity, mass: Float): PhysicsComponent {
        val (min, max) = owner.minMaxWorld
        val halfExtends = Vector3f(max.x - min.x, max.y - min.y, max.z - min.z)
        halfExtends.scale(0.5f)
        return addBoxPhysicsComponent(owner, halfExtends, mass)
    }

    fun addBoxPhysicsComponent(owner: Entity, halfExtends: Float, mass: Float): PhysicsComponent {
        return addBoxPhysicsComponent(owner, Vector3f(halfExtends, halfExtends, halfExtends), mass)
    }

    fun addBoxPhysicsComponent(owner: Entity, halfExtends: Vector3f, mass: Float): PhysicsComponent {
        val boxShape = BoxShape(halfExtends)
        val inertia = Vector3f()
        boxShape.calculateLocalInertia(1f, inertia)
        return addPhysicsComponent(MeshShapeInfo({ boxShape }, owner, mass, inertia))
    }

    fun addHullPhysicsComponent(owner: Entity, mass: Float): PhysicsComponent {
        throw IllegalStateException("Currently not implemented!")
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

    fun addMeshPhysicsComponent(owner: Entity, mass: Float): PhysicsComponent {
        val inertia = Vector3f()
        val collisionShapeSupplier = { supplyCollisionShape(owner, mass, inertia) }

        val info = MeshShapeInfo(collisionShapeSupplier, owner, mass, inertia)
        return addPhysicsComponent(info)
    }

    fun supplyCollisionShape(owner: Entity, mass: Float, inertia: Vector3f): CollisionShape {
        throw IllegalStateException("Currently not implemented!")
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

    fun debugDrawWorld() {
        dynamicsWorld!!.debugDrawWorld()
    }

    override fun update(scop: CoroutineScope, deltaSeconds: Float) {

    }

    override fun clear() {

    }

    override fun onEntityAdded(entities: List<Entity>) {

    }

    override fun afterUpdate(scope: CoroutineScope, deltaSeconds: Float) {

    }

    override fun render(result: DrawResult, state: RenderState) {
        if (config.debug.isDrawLines) {
            renderer.drawAllLines { program ->
                program.setUniform("diffuseColor", org.joml.Vector3f(1f, 1f, 0f))
                debugDrawWorld()
            }
        }
    }

    inner class MeshShapeInfo(var shapeSupplier: Supplier<CollisionShape>, var owner: Entity, var mass: Float, var inertia: Vector3f)

    fun addPhysicsComponent(info: MeshShapeInfo): PhysicsComponent {
        return PhysicsComponent(info.owner, info, this)
    }


    private fun setupBullet(renderer: Renderer<*>, gravity: Vector3f) {
        val broadphase = DbvtBroadphase()
        val collisionConfiguration = DefaultCollisionConfiguration()
        val dispatcher = CollisionDispatcher(collisionConfiguration)
        val constraintSolver = SequentialImpulseConstraintSolver()
        dynamicsWorld = object : DiscreteDynamicsWorld(dispatcher, broadphase, constraintSolver, collisionConfiguration) {
            override fun debugDrawObject(worldTransform: Transform, shape: CollisionShape?, color: Vector3f?) {
                super.debugDrawObject(worldTransform, shape, color)
                val from = Vector3f()
                val to = Vector3f()
                shape!!.getAabb(worldTransform, from, to)
                from.negate()
                getDebugDrawer().drawAabb(from, to, color)
            }
        }
        dynamicsWorld!!.setGravity(gravity)
        dynamicsWorld!!.debugDrawer = object : IDebugDraw() {
            internal var logger = Logger.getLogger("Physics Factory Debug Draw")

            override fun setDebugMode(arg0: Int) {}

            override fun reportErrorWarning(arg0: String) {
                logger.info(arg0)
            }

            override fun getDebugMode(): Int {
                val flags = DebugDrawModes.DRAW_AABB//                        & DebugDrawModes.DRAW_WIREFRAME
                //                            DebugDrawModes.DRAW_TEXT
                //                        & DebugDrawModes.DRAW_CONTACT_POINTS
                //                         DebugDrawModes.MAX_DEBUG_DRAW_MODE
                return if (config.debug.isDrawLines) flags else 0
            }

            override fun drawLine(start: Vector3f, end: Vector3f, color: Vector3f) {
                renderer.batchLine(
                        org.joml.Vector3f(start.x, start.y, start.z),
                        org.joml.Vector3f(end.x, end.y, end.z))
            }

            override fun drawAabb(from: Vector3f, to: Vector3f, color: Vector3f) {

                drawLine(from, Vector3f(to.x, from.y, from.z), color)
                drawLine(from, Vector3f(from.x, to.y, from.z), color)
                drawLine(from, Vector3f(to.x, to.y, from.z), color)

                drawLine(Vector3f(from.x, to.y, to.z), to, color)
                drawLine(Vector3f(to.x, from.y, to.z), to, color)
                drawLine(Vector3f(from.x, from.y, to.z), to, color)

                drawLine(from, to, color)
            }

            override fun drawContactPoint(arg0: Vector3f, arg1: Vector3f, arg2: Float,
                                          arg3: Int, arg4: Vector3f) {
            }

            override fun draw3dText(arg0: Vector3f, arg1: String) {
                logger.info("$arg0 - $arg1")
            }
        }

        val groundShape = StaticPlaneShape(javax.vecmath.Vector3f(0f, 1f, 0f), 0.25f)
        val inertia = Vector3f()
        groundShape.calculateLocalInertia(0f, inertia)
        val transform = Transform(Matrix4f(Quat4f(), Vector3f(0f, -40f, 0f), 1f))
        val groundMotionState = DefaultMotionState(transform)
        val groundBodyConstructionInfo = RigidBodyConstructionInfo(0f, groundMotionState, groundShape, inertia)
        groundBodyConstructionInfo.restitution = 0.25f
        ground = RigidBody(groundBodyConstructionInfo)
        dynamicsWorld!!.addRigidBody(ground)
    }

    fun registerRigidBody(rigidBody: RigidBody) {
        rigidBodyCache.add(rigidBody)
        dynamicsWorld!!.addRigidBody(rigidBody)
    }

    fun clearWorld() {
        for (rigidBody in rigidBodyCache) {
            unregisterRigidBody(rigidBody)
        }
    }

    fun unregisterRigidBody(rigidBody: RigidBody): CompletableFuture<Any> {
        return commandQueue.addCommand(object : FutureCallable<Any>() {
            @Throws(Exception::class)
            override fun execute(): Any? {
                dynamicsWorld!!.removeRigidBody(rigidBody)
                return null
            }
        })
    }
}
