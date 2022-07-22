package de.hanno.hpengine.transform

import de.hanno.hpengine.Parentable
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import java.io.Serializable
import java.util.ArrayList

class Transform() : Matrix4f(), Parentable<Transform>, Serializable {

    init {
        identity()
    }

    constructor(source: Transform?) : this() {
        this.set(source)
    }

    override var parent: Transform? = null
        set(value) {
            value?.let { value ->
                check(!(hasParent && parent!!.nodeAlreadyParentedSomewhere(value))) { "Cannot parent myself" }
            }
            field = value
        }

    protected fun nodeAlreadyParentedSomewhere(node: Transform): Boolean {
        return if (hasParent) {
            parent!!.nodeAlreadyParentedSomewhere(node)
        } else node === this
    }

    override val children: MutableList<Transform> = ArrayList()

    override fun addChild(child: Transform) {
        if(!hasChildInHierarchy(child)) {
            children.add(child)
        }
    }

    override fun removeChild(child: Transform) {
        children.remove(child)
    }
    val transformation: Matrix4f
        get() {
            return parent?.let { parent ->
                Matrix4f(parent.transformation).mul(this)
            } ?: this
        }

    override fun equals(b: Any?): Boolean {
        if (b !is Transform) {
            return false
        }
        val other = b
        return equals(position, other.position) && equals(orientation, other.orientation)
    }

    var orientation: Quaternionf
        get() = rotation
        set(rotation) {
            val eulerAngles = Vector3f()
            rotation.getEulerAnglesXYZ(eulerAngles)
            setRotationXYZ(eulerAngles.x(), eulerAngles.y(), eulerAngles.z())
        }

    private fun equals(a: Vector3f, b: Vector3f): Boolean {
        return a.x == b.x && a.y == b.y && a.z == b.z
    }

    private fun equals(a: Quaternionf, b: Quaternionf): Boolean {
        return a.x == b.x && a.y == b.y && a.z == b.z && a.w == b.w
    }

    var _position = Vector3f()
    var position: Vector3f
        get() {
            return transformation.getTranslation(_position)
        }
        set(value) {
            _position = value
        }

    val rightDirection: Vector3f
        get() = this.transformDirection(Vector3f(1f, 0f, 0f)).normalize()
    val upDirection: Vector3f
        get() = this.transformDirection(Vector3f(0f, 1f, 0f)).normalize()
    val viewDirection: Vector3f
        get() = this.transformDirection(Vector3f(0f, 0f, 1f)).normalize()
    val rotation: Quaternionf
        get() {
            val rotation = Quaternionf()
            return transformation.getNormalizedRotation(rotation)
        }
    val scale: Vector3f
        get() {
            val scale = Vector3f()
            return transformation.getScale(scale)
        }
    val center: Vector3f
        get() {
            val position = Vector3f()
            return transformation.getTranslation(position)
        }

    fun rotate(axisAngle: Vector4f) {
        rotate(axisAngle.w, axisAngle.x, axisAngle.y, axisAngle.z)
    }

    fun rotate(axis: Vector3f, angleInDegrees: Int) {
        rotate(Math.toRadians(angleInDegrees.toDouble()).toFloat(), axis.x, axis.y, axis.z)
    }

    fun rotateAround(axis: Vector3f, angleInRad: Float, pivot: Vector3f) {
        rotateAround(Quaternionf().setAngleAxis(angleInRad, axis.x, axis.y, axis.z), pivot.x, pivot.y, pivot.z)
    }

    companion object {
        val IDENTITY: Transform = Transform()
        private const val serialVersionUID = 1L
        val WORLD_RIGHT = Vector3f(1f, 0f, 0f)
        val WORLD_UP = Vector3f(0f, 1f, 0f)
        val WORLD_VIEW = Vector3f(0f, 0f, 1f)
    }
}