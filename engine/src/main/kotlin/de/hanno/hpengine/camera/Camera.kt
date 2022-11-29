package de.hanno.hpengine.camera

import de.hanno.hpengine.graphics.shader.safePut
import de.hanno.hpengine.Transform
import de.hanno.hpengine.util.Util
import org.joml.*
import org.lwjgl.BufferUtils

class Camera(
    val transform: Transform,
    ratio: Float = 1280f/720f
) {

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
        get() = transform.transformation.invert(field)
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
        init(Util.createPerspective(45f, ratio, this.near, this.far), this.near, this.far, 60f, ratio, 5f,
            Defaults.focalDepth,
            Defaults.focalLength,
            Defaults.fStop
        )
    }

    constructor(transform: Transform, near: Float, far: Float, fov: Float, ratio: Float): this(transform) {
        init(Util.createPerspective(fov, ratio, near, far), near, far, fov, ratio, 5f,
            Defaults.focalDepth,
            Defaults.focalLength,
            Defaults.fStop
        )
    }

    constructor(transform: Transform, camera: Camera): this(transform) {
        init(camera)
    }

    constructor(transform: Transform, projectionMatrix: Matrix4f, near: Float, far: Float, fov: Float, ratio: Float): this(transform) {
        init(projectionMatrix, near, far, fov, ratio, 5f, Defaults.focalDepth, Defaults.focalLength, Defaults.fStop)
    }

    fun init(camera: Camera) {
        transform.set(camera.transform)
        init(camera.projectionMatrix, camera.near, camera.far,
                camera.fov, camera.ratio, camera.exposure,
                camera.focalDepth, camera.focalLength, camera.fStop)
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

    fun update(deltaSeconds: Float) {
        saveViewMatrixAsLastViewMatrix()
        projectionMatrix.mul(viewMatrix, viewProjectionMatrix) // TODO: Should move into the block below, but it's currently broken
        frustum.frustumIntersection.set(viewProjectionMatrix)
//        TODO: Fix this, doesn't work
//        if (entity.hasMoved())
        transform()
        storeMatrices()
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
        frustum.calculate(projectionMatrix, viewMatrix)
    }

    fun saveViewMatrixAsLastViewMatrix() = lastViewMatrixAsBuffer.safePut(viewMatrixAsBuffer)

    private fun updateProjectionMatrixAndFrustum() {
        calculateProjectionMatrix()
        frustum.calculate(projectionMatrix, viewMatrix)
    }

    private fun calculateProjectionMatrix() {
        projectionMatrix = if (perspective) {
            Util.createPerspective(fov, ratio, near, far)
        } else {
            Util.createOrthogonal(-width / 2, width / 2, height / 2, -height / 2, -far / 2, far / 2)
        }
    }


    fun getViewDirection() = transform.viewDirection
    fun getRightDirection() = transform.rightDirection
    fun getUpDirection() = transform.upDirection
    fun rotation(quaternion: Quaternionf) = transform.rotation(quaternion)
    fun rotate(axisAngle4f: AxisAngle4f) = transform.rotate(axisAngle4f)
    fun getTranslation(dest: Vector3f) = transform.getTranslation(dest)
    fun translateLocal(offset: Vector3f) = transform.translateLocal(offset)
    fun setTranslation(translation: Vector3f) = transform.setTranslation(translation)
    fun getPosition() = transform.position


    companion object

    object Defaults {
        const val focalDepth = 1.84f
        const val focalLength = 51f
        const val fStop = 1.15f
        const val fov = 60f
    }
}


