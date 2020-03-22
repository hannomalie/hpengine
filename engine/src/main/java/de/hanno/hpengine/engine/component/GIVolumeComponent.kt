package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.extensibleDeferredRenderer
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.VoxelConeTracingExtension
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.util.Util
import kotlinx.coroutines.CoroutineScope
import org.joml.Matrix4f
import org.joml.Vector3f

data class GIVolumeComponent(override val entity: Entity,
                        val giVolumeGrids: VoxelConeTracingExtension.GIVolumeGrids,
                        val extents: Vector3f) : Component {
    val halfExtents = Vector3f(extents).mul(0.5f)

    val orthoCam = Camera(this.entity, createOrthoMatrix(), gridSizeScaled.toFloat(), (-gridSizeScaled).toFloat(), 90f, 1f)
    val scale: Float
        get() = extents[extents.maxComponent()] / giVolumeGrids.gridSize.toFloat()

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

}

class GIVolumeSystem(val engine: EngineContext<OpenGl>,
                     scene: Scene) : SimpleEntitySystem(scene, listOf(GIVolumeComponent::class.java)) {

    val voxelConeTracingExtension: VoxelConeTracingExtension? = run {
        engine.extensibleDeferredRenderer?.let { it ->
            val voxelConeTracingExtension = VoxelConeTracingExtension(engine, it.shadowMapExtension, it)
            engine.addResourceContext.locked {
                it.extensions.add(voxelConeTracingExtension)
            }
            voxelConeTracingExtension
        }
    }

    override fun CoroutineScope.update(deltaSeconds: Float) {
        val giComponents = components[GIVolumeComponent::class.java]!! as List<GIVolumeComponent>
        if(giComponents.isNotEmpty()) {
            val globalGrid = giComponents.first()
            val halfExtents = Vector3f(scene.minMax.max).sub(scene.minMax.min).mul(0.5f)
            globalGrid.entity.position.set(Vector3f(scene.minMax.min).add(halfExtents))
            globalGrid.extents.set(halfExtents).mul(2f)
            globalGrid.halfExtents.set(globalGrid.extents).mul(0.5f)
        }
    }

    override fun extract(renderState: RenderState) {
        updateGiVolumes(renderState)
    }
    private fun updateGiVolumes(renderState: RenderState) {
        voxelConeTracingExtension?.let { voxelConeTracingExtension ->
            val componentList = components[GIVolumeComponent::class.java] as List<GIVolumeComponent>
            if (componentList.isNotEmpty()) {
                voxelConeTracingExtension.extract(renderState, componentList)
            }
        }
    }
}