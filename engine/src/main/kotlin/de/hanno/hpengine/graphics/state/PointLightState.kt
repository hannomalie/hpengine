package de.hanno.hpengine.graphics.state

import PointLightStructImpl.Companion.sizeInBytes
import PointLightStructImpl.Companion.type
import de.hanno.hpengine.artemis.PointLightComponent
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.light.point.PointLightShadowMapStrategy
import de.hanno.hpengine.graphics.light.point.PointLightStruct
import de.hanno.hpengine.graphics.buffer.typed
import org.koin.core.annotation.Single

class PointLightState(graphicsApi: GraphicsApi) {
    var pointLights: List<PointLightComponent> = listOf()
    var pointLightBuffer = graphicsApi.PersistentShaderStorageBuffer(PointLightStruct.sizeInBytes).typed(PointLightStruct.type)
    var pointLightShadowMapStrategy: PointLightShadowMapStrategy = object: PointLightShadowMapStrategy {
        override fun renderPointLightShadowMaps(renderState: RenderState) {}
        override fun bindTextures() {}
    }
    var pointLightMovedInCycle: Long = 0
}

@Single
class PointLightStateHolder(graphicsApi: GraphicsApi, renderStateContext: RenderStateContext) {
    val lightState = renderStateContext.renderState.registerState {
        PointLightState(graphicsApi)
    }
}
