package de.hanno.hpengine.model

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.state.EntitiesState
import de.hanno.hpengine.graphics.state.RenderStateContext
import org.koin.core.annotation.Single

@Single
class EntitiesStateHolder(
    graphicsApi: GraphicsApi,
    renderStateContext: RenderStateContext,
) {
    val entitiesState = renderStateContext.renderState.registerState {
        EntitiesState(graphicsApi)
    }
}