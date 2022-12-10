package de.hanno.hpengine.graphics.state

import PointLightStructImpl.Companion.sizeInBytes
import PointLightStructImpl.Companion.type
import de.hanno.hpengine.artemis.PointLightComponent
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.RenderStateContext
import de.hanno.hpengine.graphics.light.point.PointLightShadowMapStrategy
import de.hanno.hpengine.graphics.light.point.PointLightStruct
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentMappedBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.typed

context(GpuContext)
class PointLightState {
    var pointLights: List<PointLightComponent> = listOf()
    var pointLightBuffer = PersistentMappedBuffer(PointLightStruct.sizeInBytes).typed(PointLightStruct.type)
    var pointLightShadowMapStrategy: PointLightShadowMapStrategy = object: PointLightShadowMapStrategy {
        override fun renderPointLightShadowMaps(renderState: RenderState) {}
        override fun bindTextures() {}
    }
    var pointLightMovedInCycle: Long = 0

    // TODO: Remove from here
    var areaLightDepthMaps: List<Int> = listOf()
}

context(GpuContext, RenderStateContext)
class PointLightStateHolder {
    val lightState = renderState.registerState {
        PointLightState()
    }
}
