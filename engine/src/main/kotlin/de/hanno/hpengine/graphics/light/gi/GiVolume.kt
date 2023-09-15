package de.hanno.hpengine.graphics.light.gi

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.Component
import com.artemis.annotations.All
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.graphics.vct.VoxelConeTracingExtension
import de.hanno.hpengine.graphics.vct.createGIVolumeGrids
import de.hanno.hpengine.model.material.MaterialManager
import org.koin.core.annotation.Single


class GiVolumeComponent: Component()

@All(GiVolumeComponent::class)
@Single(binds=[BaseSystem::class, GiVolumeSystem::class])
class GiVolumeSystem(
    val textureManager: OpenGLTextureManager,
) : BaseEntitySystem() {
    private val grids = mutableMapOf<Int, VoxelConeTracingExtension.GIVolumeGrids>()
    override fun inserted(entityId: Int) {
        grids[entityId] = textureManager.createGIVolumeGrids()
    }

    override fun removed(entityId: Int) {
        grids.remove(entityId)
    }

    override fun processSystem() {}

    // TODO: Implement extraction to a GiVolumeStateHolder here
}

@Single
class GiVolumeStateHolder(
    renderStateContext: RenderStateContext
) {
    val giVolumesState = renderStateContext.renderState.registerState {
        GiVolumesState()
    }
}
class GiVolumesState {
    // Implement needed state here, like transform etc
    val volumes = listOf<Unit>()
}
