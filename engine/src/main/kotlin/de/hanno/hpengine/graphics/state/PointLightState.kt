package de.hanno.hpengine.graphics.state

import PointLightStructImpl.Companion.sizeInBytes
import PointLightStructImpl.Companion.type
import de.hanno.hpengine.artemis.PointLightComponent
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.light.point.PointLightShadowMapStrategy
import de.hanno.hpengine.graphics.light.point.PointLightStruct
import de.hanno.hpengine.graphics.buffer.typed

context(GraphicsApi)
class PointLightState {
    var pointLights: List<PointLightComponent> = listOf()
    var pointLightBuffer = PersistentShaderStorageBuffer(PointLightStruct.sizeInBytes).typed(PointLightStruct.type)
    var pointLightShadowMapStrategy: PointLightShadowMapStrategy = object: PointLightShadowMapStrategy {
        override fun renderPointLightShadowMaps(renderState: RenderState) {}
        override fun bindTextures() {}
    }
    var pointLightMovedInCycle: Long = 0
}

context(GraphicsApi, RenderStateContext)
class PointLightStateHolder {
    val lightState = renderState.registerState {
        PointLightState()
    }
}
