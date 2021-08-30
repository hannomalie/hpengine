package de.hanno.hpengine.engine.component

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
import org.joml.Matrix4f
import org.joml.Vector3f

class GIVolumeComponent(override val entity: Entity,
                        var giVolumeGrids: VoxelConeTracingExtension.GIVolumeGrids) : Component {

    constructor(entity: Entity, giVolumeGrids: VoxelConeTracingExtension.GIVolumeGrids, extents: Vector3f): this(entity, giVolumeGrids) {
        spatial.boundingVolume.setLocalAABB(Vector3f(extents).mul(-0.5f), Vector3f(extents).mul(0.5f))
    }

    val spatial = TransformSpatial(entity.transform, AABB(Vector3f(-1f), Vector3f(1f)))

    val resolution: TextureDimension3D
        get() = giVolumeGrids.albedoGrid.dimension

    inline val boundingVolume: AABB
        get() = spatial.boundingVolume

    val extents: Vector3f
        get() = boundingVolume.extents

    val halfExtents: Vector3f
        get() = boundingVolume.halfExtents

    val orthoCam = Camera(this.entity, createOrthoMatrix(), gridSizeScaled.toFloat(), (-gridSizeScaled).toFloat(), 90f, 1f)
    val scale: Float
        get() = boundingVolume.extents[boundingVolume.extents.maxComponent()] / giVolumeGrids.gridSize.toFloat()

    val gridSizeScaled: Int
        get() = (giVolumeGrids.gridSize * scale).toInt()

    val gridSizeHalfScaled: Int
        get() = (giVolumeGrids.gridSize * scale * 0.5f).toInt()

    init {
        entity.spatial = spatial
    }
    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        val gridSizeHalf = this@GIVolumeComponent.halfExtents[this@GIVolumeComponent.halfExtents.maxComponent()]
        this@GIVolumeComponent.orthoCam.init(this@GIVolumeComponent.createOrthoMatrix(), gridSizeHalf, -gridSizeHalf, 90f, 1f, this@GIVolumeComponent.orthoCam.exposure, this@GIVolumeComponent.orthoCam.focalDepth, this@GIVolumeComponent.orthoCam.focalLength, this@GIVolumeComponent.orthoCam.fStop)
    }
    private fun createOrthoMatrix(): Matrix4f {
        val gridSizeHalf = halfExtents[halfExtents.maxComponent()]
        return Util.createOrthogonal(-gridSizeHalf, gridSizeHalf, gridSizeHalf, -gridSizeHalf, gridSizeHalf, -gridSizeHalf)
    }

    override fun toString(): String = "GIVolumeComponent $resolution"

}
