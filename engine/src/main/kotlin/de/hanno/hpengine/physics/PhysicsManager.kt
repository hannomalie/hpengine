package de.hanno.hpengine.physics

import Vector4fStruktImpl.Companion.sizeInBytes
import Vector4fStruktImpl.Companion.type
import com.artemis.BaseEntitySystem
import com.artemis.annotations.All
import com.bulletphysics.collision.broadphase.DbvtBroadphase
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
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver
import com.bulletphysics.linearmath.DebugDrawModes
import com.bulletphysics.linearmath.DefaultMotionState
import com.bulletphysics.linearmath.IDebugDraw
import com.bulletphysics.linearmath.Transform
import de.hanno.hpengine.artemis.PhysicsComponent
import de.hanno.hpengine.artemis.PrimaryCameraStateHolder
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.RenderStateContext
import de.hanno.hpengine.graphics.renderer.addLine
import de.hanno.hpengine.graphics.renderer.drawLines
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.graphics.shader.LinesProgramUniforms
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderSystem
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.ressources.StringBasedCodeSource
import org.joml.Vector3fc
import java.util.function.Supplier
import java.util.logging.Logger
import javax.vecmath.Matrix4f
import javax.vecmath.Quat4f
import javax.vecmath.Vector3f

context(GpuContext, RenderStateContext)
@All(PhysicsComponent::class)
class PhysicsManager(
    private val config: Config,
    private val programManager: ProgramManager,
    gravity: Vector3f = Vector3f(0f, -20f, 0f),
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
) : BaseEntitySystem(), RenderSystem {
    private val lineVertices = PersistentShaderStorageBuffer(100 * Vector4fStrukt.sizeInBytes).typed(Vector4fStrukt.type)
    val linesProgram = programManager.run {
        val uniforms = LinesProgramUniforms()
        getProgram(
            StringBasedCodeSource(
                "mvp_vertex_vec4", """
                //include(globals_structs.glsl)
                
                ${uniforms.shaderDeclarations}

                in vec4 in_Position;

                out vec4 pass_Position;
                out vec4 pass_WorldPosition;

                void main()
                {
                	vec4 vertex = vertices[gl_VertexID];
                	vertex.w = 1;

                	pass_WorldPosition = ${uniforms::modelMatrix.name} * vertex;
                	pass_Position = ${uniforms::projectionMatrix.name} * ${uniforms::viewMatrix.name} * pass_WorldPosition;
                    gl_Position = pass_Position;
                }
            """.trimIndent()
            ),
            StringBasedCodeSource(
                "simple_color_vec3", """
            ${uniforms.shaderDeclarations}

            layout(location=0)out vec4 out_color;

            void main()
            {
                out_color = vec4(${uniforms::color.name},1);
            }
        """.trimIndent()
            ), null, Defines(), uniforms
        )
    }

    val linePoints = mutableListOf<Vector3fc>()

    private var dynamicsWorld: DynamicsWorld? = null
    var ground: RigidBody? = null

    internal var rigidBodyCache: MutableList<RigidBody> = ArrayList()

    init {
        setupBullet(gravity)
    }

    override fun update(deltaSeconds: Float) {
        dynamicsWorld!!.stepSimulation(deltaSeconds)
    }

    fun createBallPhysicsComponent(radius: Float = 1f, mass: Float = 10f): PhysicsComponent {
        val sphereShape = SphereShape(radius)
        val inertia = Vector3f()
        sphereShape.calculateLocalInertia(mass, inertia)
        MeshShapeInfo({ sphereShape }, mass, inertia)
        return PhysicsComponent()
    }

    fun createBoxPhysicsComponent(entity: Int): PhysicsComponent {
        return createBallPhysicsComponent(10f)
    }

    fun createBoxPhysicsComponent(owner: Int, mass: Float, halfExtends: Vector3f): PhysicsComponent {
        return createBoxPhysicsComponent(halfExtends, mass)
    }

    fun createBoxPhysicsComponent(owner: Int, halfExtends: Float, mass: Float): PhysicsComponent {
        return createBoxPhysicsComponent(Vector3f(halfExtends, halfExtends, halfExtends), mass)
    }

    fun createBoxPhysicsComponent(halfExtends: Vector3f, mass: Float): PhysicsComponent {
        val boxShape = BoxShape(halfExtends)
        val inertia = Vector3f()
        boxShape.calculateLocalInertia(1f, inertia)
        MeshShapeInfo({ boxShape }, mass, inertia)
        return PhysicsComponent()
    }

    fun supplyCollisionShape(mass: Float, inertia: Vector3f): CollisionShape {
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

    override fun render(renderState: RenderState) {
        if (config.debug.isDrawLines) {
            val camera = renderState[primaryCameraStateHolder.camera]
            debugDrawWorld()
            drawLines(
                programManager,
                linesProgram,
                lineVertices,
                linePoints,
                viewMatrix = camera.viewMatrixAsBuffer,
                projectionMatrix = camera.projectionMatrixAsBuffer,
                color = org.joml.Vector3f(1f, 1f, 0f)
            )
        }
    }

    class MeshShapeInfo(var shapeSupplier: Supplier<CollisionShape>, var mass: Float, var inertia: Vector3f)


    private fun setupBullet(gravity: Vector3f) {
        val broadphase = DbvtBroadphase()
        val collisionConfiguration = DefaultCollisionConfiguration()
        val dispatcher = CollisionDispatcher(collisionConfiguration)
        val constraintSolver = SequentialImpulseConstraintSolver()
        dynamicsWorld =
            object : DiscreteDynamicsWorld(dispatcher, broadphase, constraintSolver, collisionConfiguration) {
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
                linePoints.addLine(
                    org.joml.Vector3f(start.x, start.y, start.z),
                    org.joml.Vector3f(end.x, end.y, end.z)
                )
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

            override fun drawContactPoint(
                arg0: Vector3f, arg1: Vector3f, arg2: Float,
                arg3: Int, arg4: Vector3f
            ) {
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

    override fun removed(entityId: Int) {
//        TODO: unregister rigid body through component removal here
//        unregisterRigidBody(rigidBody)
    }
    private fun unregisterRigidBody(rigidBody: RigidBody) {
        dynamicsWorld!!.removeRigidBody(rigidBody)
    }

    override fun processSystem() {}
}
