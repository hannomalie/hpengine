package de.hanno.hpengine.engine.camera

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.renderer.SimpleLineRenderer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.log.ConsoleLogger
import de.hanno.hpengine.util.Util
import kotlinx.coroutines.CoroutineScope
import org.joml.AxisAngle4f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import java.io.IOException
import java.io.ObjectOutputStream
import java.nio.FloatBuffer

object Defaults {
    const val focalDepth = 1.84f
    const val focalLength = 51f
    const val fStop = 1.15f
    const val fov = 60f
}

open class Camera @JvmOverloads constructor(
        override var entity: Entity,
        ratio: Float = 1280f/720f): Component {

    override val identifier = this.javaClass.simpleName

    var exposure = 5f
    var focalDepth = Defaults.focalDepth
    var focalLength = Defaults.focalLength
    var fStop = Defaults.fStop

    var ratio = ratio
        set(value) {
            field = value
            calculateProjectionMatrix()
            frustum.calculate(this)
        }
    @Transient
    var viewProjectionMatrixAsBuffer = BufferUtils.createFloatBuffer(16)
        internal set
    @Transient
    var projectionMatrixAsBuffer = BufferUtils.createFloatBuffer(16)
        internal set
    @Transient
    var lastViewMatrixAsBuffer = BufferUtils.createFloatBuffer(16)
        internal set
    val viewMatrixAsBuffer = BufferUtils.createFloatBuffer(16)

    var viewMatrix = Matrix4f()
        get() {
            return entity.transformation.invert(field)
        }
    var projectionMatrix = Matrix4f()
    var viewProjectionMatrix = Matrix4f()

    var frustum = Frustum()
        protected set

    private var near = 1f
    private var far = 7000f
    var fov = 60f
        set(value) {
            field = value
            calculateProjectionMatrix()
            frustum.calculate(this)
        }

    var width = 1600f
        set(width) {
            field = width
            calculateProjectionMatrix()
            frustum.calculate(this)
        }
    var height = 1600f
        set(height) {
            field = height
            calculateProjectionMatrix()
            frustum.calculate(this)
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
        entity.set(camera.entity)
        init(camera.projectionMatrix, camera.getNear(), camera.getFar(),
             camera.fov, camera.ratio, camera.exposure,
             camera.focalDepth, camera.focalLength, camera.fStop)
        if (camera.entity.hasParent()) {
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

        frustum.calculate(this)
        saveViewMatrixAsLastViewMatrix()
        transform()
        storeMatrices()
    }

    override fun CoroutineScope.update(deltaSeconds: Float) {
        saveViewMatrixAsLastViewMatrix()
        projectionMatrix.mul(viewMatrix, viewProjectionMatrix) // TODO: Should move into the block below, but it's currently broken
        frustum.frustumIntersection.set(viewProjectionMatrix)
//        TODO: Fix this, doesn't work
//        if (entity.hasMoved())
        this@Camera.run {
            transform()
            storeMatrices()
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

    fun saveViewMatrixAsLastViewMatrix() {
        lastViewMatrixAsBuffer.rewind()
        lastViewMatrixAsBuffer.put(viewMatrixAsBuffer)
        lastViewMatrixAsBuffer.rewind()
    }

    fun getNear(): Float {
        return near
    }

    fun setNear(near: Float) {
        this.near = near
        calculateProjectionMatrix()
        frustum.calculate(this)
    }


    fun setFar(far: Float) {
        this.far = far
        calculateProjectionMatrix()
        frustum.calculate(this)
    }

    fun getFar(): Float {
        return far
    }

    private fun calculateProjectionMatrix() {
        if (perspective) {
            projectionMatrix = Util.createPerspective(fov, ratio, near, far)
        } else {
            projectionMatrix = Util.createOrthogonal(-width / 2, width / 2, height / 2, -height / 2, -far / 2, far / 2)
        }
    }


    @Throws(IOException::class, ClassNotFoundException::class)
    private fun readObject(`in`: java.io.ObjectInputStream) {
        `in`.defaultReadObject()
        viewProjectionMatrixAsBuffer = BufferUtils.createFloatBuffer(16)
        projectionMatrixAsBuffer = BufferUtils.createFloatBuffer(16)
        lastViewMatrixAsBuffer = BufferUtils.createFloatBuffer(16)
    }


    @Throws(IOException::class)
    private fun writeObject(oos: ObjectOutputStream) {
        oos.defaultWriteObject()
    }

    fun getViewDirection() = entity.viewDirection
    fun getRightDirection() = entity.rightDirection
    fun getUpDirection() = entity.upDirection
    fun rotation(quaternion: Quaternionf) = entity.rotation(quaternion)
    fun rotate(axisAngle4f: AxisAngle4f) = entity.rotate(axisAngle4f)
    fun getTranslation(dest: Vector3f) = entity.getTranslation(dest)
    fun translateLocal(offset: Vector3f) = entity.translateLocal(offset)
    fun setTranslation(translation: Vector3f) = entity.setTranslation(translation)
    fun getPosition() = entity.position


    fun getTranslationRotationBuffer(): FloatBuffer {
        return viewMatrixAsBuffer
    }

    companion object {
        private val LOGGER = ConsoleLogger.getLogger()
    }

}

class CameraComponentSystem(val engine: Engine<*>): ComponentSystem<Camera>, RenderSystem {

    // TODO: Remove this cast
    private val lineRenderer = SimpleLineRenderer(engine as Engine<OpenGl>)
    override val componentClass: Class<Camera> = Camera::class.java
    override fun CoroutineScope.update(deltaSeconds: Float) {
        getComponents().forEach {
            with(it) {
                update(deltaSeconds)
            }
        }
    }
    private val components = mutableListOf<Camera>()
    override fun getComponents(): List<Camera> = components

    override fun create(entity: Entity) = Camera(entity, engine.config.width.toFloat() / engine.config.height.toFloat())
    fun create(entity: Entity, projectionMatrix: Matrix4f, near:Float, far:Float, fov:Float, ratio:Float, perspective:Boolean) = Camera(entity, projectionMatrix, near, far, fov, ratio).apply { this.perspective = perspective }.also { components.add(it); }

    override fun addComponent(component: Camera) {
        components.add(component)
    }
    override fun clear() = components.clear()

    override fun render(result: DrawResult, state: RenderState) {
        if (engine.config.debug.isDrawCameras) {
            //            TODO: Use renderstate somehow?
            for (i in components.indices) {
                val camera = components[i]
                val corners = camera.frustumCorners
                lineRenderer.batchLine(corners[0], corners[1])
                lineRenderer.batchLine(corners[1], corners[2])
                lineRenderer.batchLine(corners[2], corners[3])
                lineRenderer.batchLine(corners[3], corners[0])

                lineRenderer.batchLine(corners[4], corners[5])
                lineRenderer.batchLine(corners[5], corners[6])
                lineRenderer.batchLine(corners[6], corners[7])
                lineRenderer.batchLine(corners[7], corners[4])

                lineRenderer.batchLine(corners[0], corners[6])
                lineRenderer.batchLine(corners[1], corners[7])
                lineRenderer.batchLine(corners[2], corners[4])
                lineRenderer.batchLine(corners[3], corners[5])
            }
        }
    }
}
