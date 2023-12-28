package de.hanno.hpengine.graphics.light.directional

import de.hanno.hpengine.graphics.state.Box
import de.hanno.hpengine.graphics.state.EntitiesState
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext

class CachingHelper(
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    renderStateContext: RenderStateContext,
)  {
    var forceRerender = true
    var animatedRenderedInCycle: Long = 0
    var staticRenderedInCycle: Long = 0
    var anyModelEntityAddedInCycle: Long = 0

    private val anyStaticModelEntityHasMovedInCycle = renderStateContext.renderState.registerState { Box(-1L) }

    fun setStaticEntityHasMovedInCycle(currentWriteState: RenderState, cycle: Long) {
        currentWriteState[anyStaticModelEntityHasMovedInCycle].underlying = cycle
    }
    fun staticEntitiesNeedRerender(renderState: RenderState): Boolean {
        val directionalLightMovedForStatic = renderState.directionalLightMovedInCycle >= staticRenderedInCycle
        val anyStaticModelEntityHasMoved = renderState[anyStaticModelEntityHasMovedInCycle].underlying >= staticRenderedInCycle
        return forceRerender || directionalLightMovedForStatic || anyStaticModelEntityHasMoved
    }

    fun animatedEntitiesNeedRerender(
        renderState: RenderState,
        entitiesState: EntitiesState
    ): Boolean {
        val directionalLightMovedForAnimated = renderState.directionalLightMovedInCycle >= animatedRenderedInCycle
        return forceRerender || directionalLightMovedForAnimated || entitiesState.anyEntityMovedInCycle >= animatedRenderedInCycle
    }

    private val RenderState.directionalLightMovedInCycle: Long
        get() = when (this[directionalLightStateHolder.entityId].underlying) {
            -1 -> -1
            else -> {
                this[directionalLightStateHolder.directionalLightHasMovedInCycle].underlying
            }
        }

    fun extract(currentWriteState: RenderState) {
        currentWriteState[anyStaticModelEntityHasMovedInCycle].underlying = when {
            anyModelEntityAddedInCycle > currentWriteState[anyStaticModelEntityHasMovedInCycle].underlying -> anyModelEntityAddedInCycle
            else -> currentWriteState[anyStaticModelEntityHasMovedInCycle].underlying
        }
    }
}