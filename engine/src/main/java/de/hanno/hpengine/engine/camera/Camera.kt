package de.hanno.hpengine.engine.camera

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.shader.safePut
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.log.ConsoleLogger
import de.hanno.hpengine.util.Util
import org.joml.AxisAngle4f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import java.nio.FloatBuffer

open class Camera @JvmOverloads constructor(
        override val entity: Entity,
        ratio: Float = 1280f/720f): Component {

    var exposure = 5f
    var focalDepth = Defaults.focalDepth
    var focalLength = Defaults.focalLength
    var fStop = Defaults.fStop

    var ratio = ratio
        set(value) {
            field = value
            updateProjectionMatrixAndFrustum()
        }
    var viewProjectionMatrixAsBuffer = BufferUtils.createFloatBuffer(16)
    var projectionMatrixAsBuffer = BufferUtils.createFloatBuffer(16)
    var lastViewMatrixAsBuffer = BufferUtils.createFloatBuffer(16)
    val viewMatrixAsBuffer = BufferUtils.createFloatBuffer(16)

    var viewMatrix = Matrix4f()
        get() {
            return entity.transform.transformation.invert(field)
        }
    var projectionMatrix = Matrix4f()
    var viewProjectionMatrix = Matrix4f()

    var frustum = Frustum()

    var near = 0.1f
        set(value) {
            field = value
            updateProjectionMatrixAndFrustum()
        }
    var far = 2000f
        set(value) {
            field = value
            updateProjectionMatrixAndFrustum()
        }

    var fov = Defaults.fov
        set(value) {
            field = value
            updateProjectionMatrixAndFrustum()
        }

    var width = 1600f
        set(width) {
            field = width
            updateProjectionMatrixAndFrustum()
        }
    var height = 1600f
        set(height) {
            field = height
            updateProjectionMatrixAndFrustum()
        }

    var perspective = true

    val frustumCorners: Array<Vector3f>
        get() {
            val inverseProjection = projectionMatrix.invert(Matrix4f())
            val result = mutableListOf<Vector4f>(inverseProjection.transform(Vector4f(-1f, 1f, 1f, 1f)),
                    inverseProjection.transform(Vector4f(1f, 1f, 1f, 1f)),
                    inverseProjection.transform(Vector4f(1f, -1f, 1f, 1f)),
                    inverseProjection.transform(Vector4f(-1f, -1f, 1f, 1f)),
                    inverseProjection.transform(Vector4f(-1f, 1f, -1f, 1f)),
                    inverseProjection.transform(Vector4f(1f, 1f, -1f, 1f)),
                    inverseProjection.transform(Vector4f(1f, -1f, -1f, 1f)),
                    inverseProjection.transform(Vector4f(-1f, -1f, -1f, 1f)))

            val inverseView = viewMatrix.invert(Matrix4f())
            val resultVec3 = result.map { it.div(it.w) }.map { inverseView.transform(it) }.map { Vector3f(it.x, it.y, it.z) }

            return resultVec3.toTypedArray()
        }

    init {
        init(Util.createPerspective(45f, ratio, this.near, this.far), this.near, this.far, 60f, ratio, 5f, Defaults.focalDepth, Defaults.focalLength, Defaults.fStop)
    }

    constructor(entity: Entity, near: Float, far: Float, fov: Float, ratio: Float): this(entity) {
        init(Util.createPerspective(fov, ratio, near, far), near, far, fov, ratio, 5f, Defaults.focalDepth, Defaults.focalLength, Defaults.fStop)
    }

    constructor(entity: Entity, camera: Camera): this(entity) {
        init(camera)
    }

    constructor(entity: Entity, projectionMatrix: Matrix4f, near: Float, far: Float, fov: Float, ratio: Float): this(entity) {
        init(projectionMatrix, near, far, fov, ratio, 5f, Defaults.focalDepth, Defaults.focalLength, Defaults.fStop)
    }

    fun init(camera: Camera) {
        entity.transform.set(camera.entity.transform)
        init(camera.projectionMatrix, camera.near, camera.far,
                camera.fov, camera.ratio, camera.exposure,
                camera.focalDepth, camera.focalLength, camera.fStop)

        if (camera.entity.hasParent) {
            val formerParent = camera.entity.parent
            entity.removeParent()
            entity.parent = formerParent
        }
    }

    fun init(projectionMatrix: Matrix4f, near: Float, far: Float, fov: Float, ratio: Float,
             exposure: Float, focalDepth: Float, focalLength: Float, fStop: Float) {

        this.exposure = exposure
        this.focalDepth = focalDepth
        this.focalLength = focalLength
        this.fStop = fStop

        this.near = near
        this.far = far
        this.fov = fov
        this.ratio = ratio
        this.projectionMatrix.set(projectionMatrix)

        saveViewMatrixAsLastViewMatrix()
        transform()
        storeMatrices()
    }

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        this@Camera.saveViewMatrixAsLastViewMatrix()
        this@Camera.projectionMatrix.mul(this@Camera.viewMatrix, this@Camera.viewProjectionMatrix) // TODO: Should move into the block below, but it's currently broken
        this@Camera.frustum.frustumIntersection.set(this@Camera.viewProjectionMatrix)
//        TODO: Fix this, doesn't work
//        if (entity.hasMoved())
        this@Camera.run {
            this.transform()
            this.storeMatrices()
        }
    }

    private fun storeMatrices() {

        viewMatrixAsBuffer.rewind()
        viewMatrix.get(viewMatrixAsBuffer)
        viewMatrixAsBuffer.rewind()

        projectionMatrixAsBuffer.rewind()
        projectionMatrix.get(projectionMatrixAsBuffer)
        projectionMatrixAsBuffer.rewind()

        viewProjectionMatrixAsBuffer.rewind()
        viewProjectionMatrix.set(projectionMatrix).mul(viewMatrix).get(viewProjectionMatrixAsBuffer)
        viewProjectionMatrixAsBuffer.rewind()
    }

    private fun transform() {
        frustum.calculate(this)
    }

    fun saveViewMatrixAsLastViewMatrix() = lastViewMatrixAsBuffer.safePut(viewMatrixAsBuffer)

    private fun updateProjectionMatrixAndFrustum() {
        calculateProjectionMatrix()
        frustum.calculate(this)
    }

    private fun calculateProjectionMatrix() {
        if (perspective) {
            projectionMatrix = Util.createPerspective(fov, ratio, near, far)
        } else {
            projectionMatrix = Util.createOrthogonal(-width / 2, width / 2, height / 2, -height / 2, -far / 2, far / 2)
        }
    }


    fun getViewDirection() = entity.transform.viewDirection
    fun getRightDirection() = entity.transform.rightDirection
    fun getUpDirection() = entity.transform.upDirection
    fun rotation(quaternion: Quaternionf) = entity.transform.rotation(quaternion)
    fun rotate(axisAngle4f: AxisAngle4f) = entity.transform.rotate(axisAngle4f)
    fun getTranslation(dest: Vector3f) = entity.transform.getTranslation(dest)
    fun translateLocal(offset: Vector3f) = entity.transform.translateLocal(offset)
    fun setTranslation(translation: Vector3f) = entity.transform.setTranslation(translation)
    fun getPosition() = entity.transform.position


    fun getTranslationRotationBuffer(): FloatBuffer {
        return viewMatrixAsBuffer
    }

    companion object {
        private val LOGGER = ConsoleLogger.getLogger()
    }

    object Defaults {
        const val focalDepth = 1.84f
        const val focalLength = 51f
        const val fStop = 1.15f
        const val fov = 60f
    }
}


