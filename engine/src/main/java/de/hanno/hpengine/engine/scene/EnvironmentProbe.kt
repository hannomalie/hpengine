package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.renderer.environmentsampler.EnvironmentSampler
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.TransformSpatial
import org.joml.Vector3f
import org.joml.Vector3fc

class EnvironmentProbe(override val entity: Entity,
                       size: Vector3f,
                       var probeUpdate: Update = Update.DYNAMIC,
                       var weight: Float = 1f) : Component {

    lateinit var sampler: EnvironmentSampler
    enum class Update {
        STATIC, DYNAMIC
    }

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        if(!::sampler.isInitialized) return
        sampler.resetDrawing()
    }
    val spatial = TransformSpatial(entity.transform, AABB(Vector3f(size).div(2f).negate(), Vector3f(size).div(2f)))
    val box = spatial.boundingVolume

    fun move(amount: Vector3f) {
        box.move(amount)
    }

    fun contains(min: Vector3fc?, max: Vector3fc?): Boolean {
        return box.contains(min!!) && box.contains(max!!)
    }

    operator fun contains(minMaxWorld: AABB): Boolean {
        return contains(minMaxWorld.min, minMaxWorld.max)
    }

    fun setUpdate(update: Update) {
        probeUpdate = update
    }

    val size: Vector3f
        get() = Vector3f(box.extents)
    val camera: Camera
        get() = sampler.camera

}