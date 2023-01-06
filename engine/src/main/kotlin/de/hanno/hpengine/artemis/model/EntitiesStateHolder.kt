package de.hanno.hpengine.artemis.model

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.state.EntitiesState
import de.hanno.hpengine.graphics.state.RenderStateContext

context(GraphicsApi, RenderStateContext)
class EntitiesStateHolder {
    val entitiesState = renderState.registerState {
        EntitiesState()
    }
}