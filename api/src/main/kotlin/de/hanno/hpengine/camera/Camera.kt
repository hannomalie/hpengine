package de.hanno.hpengine.camera

import de.hanno.hpengine.buffers.safePut
import de.hanno.hpengine.Transform
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.math.createOrthogonal
import de.hanno.hpengine.math.createPerspective
import org.joml.*
import org.koin.core.annotation.Single
import org.lwjgl.BufferUtils

class Camera(
    val transform: Transform,
    ratio: Float = 1280f/720f
) {
    var lensFlare = true
    var bloom = true
    var dof = true
    var autoExposure = true
    var exposure = 1f
    var focalDepth = Defaults.focalDepth
    var focalLength = Defaults.focalLength
    var fStop = Defaults.fStop

    var ratio = ratio
        set(value) {
            field = value
            updateProjectionMatrixAndFrustum()
        }
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
        set(value) {
            field = value
            updateProjectionMatrixAndFrustum()
        }

    // TODO: Move buffers to separate component
    var viewProjectionMatrixBuffer = BufferUtils.createFloatBuffer(16)
    var projectionMatrixBuffer = BufferUtils.createFloatBuffer(16)
    var lastViewMatrixBuffer = BufferUtils.createFloatBuffer(16)
    val viewMatrixBuffer = BufferUtils.createFloatBuffer(16)

    var viewMatrix = Matrix4f()
        get() = transform.transformation.invert(field)
    var projectionMatrix = Matrix4f()
    var viewProjectionMatrix = Matrix4f()
    var frustum = Frustum()

    val frustumCorners: Array<Vector3f>
        get() {
            val inverseProjection = projectionMatrix.invert(Matrix4f())
            val result = mutableListOf<Vector4f>(
                inverseProjection.transform(Vector4f(-1f, 1f, 1f, 1f)),
                inverseProjection.transform(Vector4f(1f, 1f, 1f, 1f)),
                inverseProjection.transform(Vector4f(1f, -1f, 1f, 1f)),
                inverseProjection.transform(Vector4f(-1f, -1f, 1f, 1f)),
                inverseProjection.transform(Vector4f(-1f, 1f, -1f, 1f)),
                inverseProjection.transform(Vector4f(1f, 1f, -1f, 1f)),
                inverseProjection.transform(Vector4f(1f, -1f, -1f, 1f)),
                inverseProjection.transform(Vector4f(-1f, -1f, -1f, 1f))
            )

            val inverseView = viewMatrix.invert(Matrix4f())
            val resultVec3 =
                result.map { it.div(it.w) }.map { inverseView.transform(it) }.map { Vector3f(it.x, it.y, it.z) }

            return resultVec3.toTypedArray()
        }

    init {
        update()
    }

    fun setFrom(camera: Camera) {
        transform.set(camera.transform)
        lensFlare = camera.lensFlare
        bloom = camera.bloom
        dof = camera.dof
        autoExposure = camera.autoExposure
        exposure = camera.exposure
        focalDepth = camera.focalDepth
        focalLength = camera.focalLength
        fStop = camera.fStop
        ratio = camera.ratio
        near = camera.near
        far = camera.far
        fov = camera.fov
        storeMatrices()
        calculateFrustum()
    }

    fun update() {
        saveViewMatrixAsLastViewMatrix()
        projectionMatrix.mul(
            viewMatrix,
            viewProjectionMatrix
        ) // TODO: Should move into the block below, but it's currently broken
        frustum.frustumIntersection.set(viewProjectionMatrix)
        transform()
        storeMatrices()
    }

    internal fun storeMatrices() {
        viewMatrixBuffer.rewind()
        viewMatrix.get(viewMatrixBuffer)
        viewMatrixBuffer.rewind()

        projectionMatrixBuffer.rewind()
        projectionMatrix.get(projectionMatrixBuffer)
        projectionMatrixBuffer.rewind()

        viewProjectionMatrixBuffer.rewind()
        viewProjectionMatrix.set(projectionMatrix).mul(viewMatrix).get(viewProjectionMatrixBuffer)
        viewProjectionMatrixBuffer.rewind()
    }

    private fun transform() {
        calculateFrustum()
    }

    private fun saveViewMatrixAsLastViewMatrix() = lastViewMatrixBuffer.safePut(viewMatrixBuffer)

    private fun updateProjectionMatrixAndFrustum() {
        calculateProjectionMatrix()
        calculateFrustum()
    }

    private fun calculateFrustum() {
        frustum.calculate(projectionMatrix, viewMatrix)
    }

    private fun calculateProjectionMatrix() {
        projectionMatrix = if (perspective) {
            createPerspective(fov, ratio, near, far)
        } else {
            createOrthogonal(-width / 2, width / 2, height / 2, -height / 2, -far / 2, far / 2)
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


@Single
class CameraComponentsStateHolder(
    renderStateContext: RenderStateContext
) {
    val frustumLines = renderStateContext.renderState.registerState { mutableListOf<Vector3fc>() }
}

