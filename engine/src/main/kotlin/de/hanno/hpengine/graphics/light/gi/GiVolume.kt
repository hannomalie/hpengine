package de.hanno.hpengine.graphics.light.gi

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.Component
import com.artemis.annotations.All
import de.hanno.hpengine.graphics.constants.MagFilter
import de.hanno.hpengine.graphics.constants.MinFilter
import de.hanno.hpengine.graphics.constants.WrapMode
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.graphics.texture.Texture3D
import de.hanno.hpengine.graphics.texture.TextureManager
import de.hanno.hpengine.model.material.MaterialManager
import org.koin.core.annotation.Single


class GiVolumeComponent: Component()

@All(GiVolumeComponent::class)
@Single(binds=[BaseSystem::class, GiVolumeSystem::class])
class GiVolumeSystem(
    val textureManager: OpenGLTextureManager,
) : BaseEntitySystem() {
    private val grids = mutableMapOf<Int, GIVolumeGrids>()
    override fun inserted(entityId: Int) {
        grids[entityId] = textureManager.createGIVolumeGrids()
    }

    override fun removed(entityId: Int) {
        grids.remove(entityId)
    }

    override fun processSystem() {}

    // TODO: Implement extraction to a GiVolumeStateHolder here
}

fun TextureManager.createGIVolumeGrids(gridSize: Int = 256) = GIVolumeGrids(
    getTexture3D(
        gridSize,
        gridTextureFormatSized,
        MinFilter.LINEAR_MIPMAP_LINEAR,
        MagFilter.LINEAR,
        WrapMode.ClampToEdge
    ),
    getTexture3D(
        gridSize,
        indexGridTextureFormatSized,
        MinFilter.NEAREST,
        MagFilter.NEAREST,
        WrapMode.ClampToEdge
    ),
    getTexture3D(
        gridSize,
        gridTextureFormatSized,
        MinFilter.LINEAR_MIPMAP_LINEAR,
        MagFilter.LINEAR,
        WrapMode.ClampToEdge
    ),
    getTexture3D(
        gridSize,
        gridTextureFormatSized,
        MinFilter.LINEAR_MIPMAP_LINEAR,
        MagFilter.LINEAR,
        WrapMode.ClampToEdge
    )
)
val gridTextureFormatSized = InternalTextureFormat.RGBA8//GL30.GL_R32UI;
val indexGridTextureFormatSized = InternalTextureFormat.R16I//GL30.GL_R32UI;

data class GIVolumeGrids(val grid: Texture3D,
                         val indexGrid: Texture3D,
                         val albedoGrid: Texture3D,
                         val normalGrid: Texture3D
) {

    val gridSize: Int = albedoGrid.dimension.width
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
