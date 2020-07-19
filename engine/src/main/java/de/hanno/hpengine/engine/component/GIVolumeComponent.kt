package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.extensibleDeferredRenderer
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.VoxelConeTracingExtension
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.texture.TextureDimension3D
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.TransformSpatial
import de.hanno.hpengine.util.Util
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.joml.Matrix4f
import org.joml.Vector3f

class GIVolumeComponent(override val entity: Entity,
                        var giVolumeGrids: VoxelConeTracingExtension.GIVolumeGrids) : Component {

    constructor(entity: Entity, giVolumeGrids: VoxelConeTracingExtension.GIVolumeGrids, extents: Vector3f): this(entity, giVolumeGrids) {
        spatial.minMaxLocal.min.set(extents).mul(-0.5f)
        spatial.minMaxLocal.max.set(extents).mul(0.5f)
    }

    val spatial = TransformSpatial(entity, AABB(Vector3f(-1f), Vector3f(1f)))

    val resolution: TextureDimension3D
        get() = giVolumeGrids.albedoGrid.dimension

    val minMax: AABB
        get() = spatial.minMaxLocal

    val extents: Vector3f
        get() = minMax.extents

    val halfExtents: Vector3f
        get() = minMax.halfExtents

    val orthoCam = Camera(this.entity, createOrthoMatrix(), gridSizeScaled.toFloat(), (-gridSizeScaled).toFloat(), 90f, 1f)
    val scale: Float
        get() = minMax.extents[minMax.extents.maxComponent()] / giVolumeGrids.gridSize.toFloat()

    val gridSizeScaled: Int
        get() = (giVolumeGrids.gridSize * scale).toInt()

    val gridSizeHalfScaled: Int
        get() = (giVolumeGrids.gridSize * scale * 0.5f).toInt()

    override fun CoroutineScope.update(deltaSeconds: Float) {
        val gridSizeHalf = halfExtents[halfExtents.maxComponent()]
        orthoCam.init(createOrthoMatrix(), gridSizeHalf, -gridSizeHalf, 90f, 1f, orthoCam.exposure, orthoCam.focalDepth, orthoCam.focalLength, orthoCam.fStop)
    }
    private fun createOrthoMatrix(): Matrix4f {
        val gridSizeHalf = halfExtents[halfExtents.maxComponent()]
        return Util.createOrthogonal(-gridSizeHalf, gridSizeHalf, gridSizeHalf, -gridSizeHalf, gridSizeHalf, -gridSizeHalf)
    }

    override fun toString(): String = "GIVolumeComponent $resolution"

}

class GIVolumeSystem(val engine: EngineContext<OpenGl>,
                     scene: Scene) : SimpleEntitySystem(scene, listOf(GIVolumeComponent::class.java)) {

    val voxelConeTracingExtension: VoxelConeTracingExtension? = run {
        engine.extensibleDeferredRenderer?.let { renderer ->
            val voxelConeTracingExtension = VoxelConeTracingExtension(engine, renderer.shadowMapExtension, renderer, renderer.extensions.firstIsInstance())
            engine.addResourceContext.locked {
                renderer.extensions.add(voxelConeTracingExtension)
            }
            voxelConeTracingExtension
        }
    }

    override fun CoroutineScope.update(deltaSeconds: Float) {
        val giComponents = components.filterIsInstance<GIVolumeComponent>()
        if(giComponents.isNotEmpty()) {
            val globalGrid = giComponents.first()
            val halfExtents = Vector3f(scene.minMax.max).sub(scene.minMax.min).mul(0.5f)
            globalGrid.entity.translation(Vector3f(scene.minMax.min).add(halfExtents))
            globalGrid.minMax.min.set(scene.minMax.min)
            globalGrid.minMax.max.set(scene.minMax.max)
        }
    }

    override fun extract(renderState: RenderState) {
        updateGiVolumes(renderState)
    }
    private fun updateGiVolumes(renderState: RenderState) {
        voxelConeTracingExtension?.let { voxelConeTracingExtension ->
            val componentList = components.filterIsInstance<GIVolumeComponent>()
            if (componentList.isNotEmpty()) {
                voxelConeTracingExtension.extract(renderState, componentList)
            }
        }
    }
}