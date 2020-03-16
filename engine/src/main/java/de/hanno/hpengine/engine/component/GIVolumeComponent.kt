package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.extensibleDeferredRenderer
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.VoxelConeTracingExtension
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.Scene
import kotlinx.coroutines.CoroutineScope
import org.joml.Vector3f

class GIVolumeComponent(override val entity: Entity,
                        val giVolumeGrids: VoxelConeTracingExtension.GIVolumeGrids,
                        val extents: Vector3f) : Component

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
            globalGrid.extents.set(scene.minMax.extents).mul(2f)
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