package de.hanno.hpengine.artemis

import com.artemis.Component
//import com.bulletphysics.dynamics.RigidBody
//import com.bulletphysics.dynamics.RigidBodyConstructionInfo
//import com.bulletphysics.linearmath.DefaultMotionState
//import com.bulletphysics.linearmath.MotionState
//import com.bulletphysics.linearmath.Transform
//import de.hanno.hpengine.engine.component.BaseComponent
//import de.hanno.hpengine.engine.entity.Entity
//import de.hanno.hpengine.Update
//import de.hanno.hpengine.PhysicsManager
//import de.hanno.hpengine.PhysicsManager.MeshShapeInfo
//import de.hanno.hpengine.util.Util
//import de.hanno.hpengine.commandqueue.FutureCallable
//import kotlinx.coroutines.CoroutineScope
//import java.util.concurrent.ExecutionException
//import javax.vecmath.Matrix4f

class PhysicsComponent: Component() {
}

// TODO: Reimplement
//class PhysicsComponent(owner: Entity, meshShapeInfo: MeshShapeInfo, physicsManager: PhysicsManager) :
//    BaseComponent(owner) {
//    @kotlin.jvm.Transient
//    private val initialTransform: Transform
//    var rigidBodyConstructionInfo: RigidBodyConstructionInfo? = null
//    private val owner: Entity
//
//    //  TODO: Recode this in PhysicsManager
//    //    public void reset(EngineContext engineContext) {
//    //        engineContext.getBackend().getPhysicsManager().unregisterRigidBody(rigidBody);
//    //        actuallyCreatePhysicsObject();
//    //    }
//    @kotlin.jvm.Transient
//    var rigidBody: RigidBody? = null
//        private set
//
//    @kotlin.jvm.Transient
//    private val info: MeshShapeInfo
//    private val physicsManager: PhysicsManager
//    private fun actuallyCreatePhysicsObject() {
//        val motionState: MotionState = DefaultMotionState(initialTransform)
//        rigidBodyConstructionInfo =
//            RigidBodyConstructionInfo(info.mass, motionState, info.shapeSupplier.get(), info.inertia)
//        rigidBodyConstructionInfo!!.restitution = 0.5f
//        rigidBody = RigidBody(rigidBodyConstructionInfo)
//        rigidBody!!.userPointer = owner
//        registerRigidBody()
//    }
//
//    private fun registerRigidBody() {
//        try {
//            physicsManager.commandQueue.addCommand<Any>(object : FutureCallable<Any?>() {
//                @Throws(Exception::class)
//                override fun execute(): Any {
//                    physicsManager.registerRigidBody(rigidBody!!)
//                    return null
//                }
//            }).get()
//        } catch (e: InterruptedException) {
//            e.printStackTrace()
//        } catch (e: ExecutionException) {
//            e.printStackTrace()
//        }
//    }
//
//    fun update(scope: CoroutineScope, deltaSeconds: Float) {
//        val out = Transform()
//        rigidBody!!.getWorldTransform(out)
//        val temp = Matrix4f()
//        out.getMatrix(temp)
//        val converted = Util.fromBullet(out)
//        owner.transform.set(converted)
//    }
//
//    val isDynamic: Boolean
//        get() = !isStatic
//    private val isStatic: Boolean
//        private get() = rigidBody!!.invMass == Float.POSITIVE_INFINITY
//
//    companion object {
//        val COMPONENT_KEY = PhysicsComponent::class.java.simpleName
//    }
//
//    init {
//        if (isDynamic) {
//            owner.updateType = Update.DYNAMIC
//        }
//        this.owner = owner
//        info = meshShapeInfo
//        this.physicsManager = physicsManager
//        owner.addComponent(this)
//        initialTransform = Util.toBullet(owner.transform)
//        actuallyCreatePhysicsObject() // This probably has to be moved to physsicsmanaqger/physicssystem
//    }
//}