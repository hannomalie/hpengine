package de.hanno.hpengine.graphics.state

import de.hanno.hpengine.artemis.PointLightComponent
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.light.point.PointLightShadowMapStrategy
import de.hanno.hpengine.graphics.light.point.PointLightStruct
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentMappedStructBuffer

class LightState(gpuContext: GpuContext<*>) {
    var pointLights: List<PointLightComponent> = listOf()
    var pointLightBuffer = PersistentMappedStructBuffer(0, gpuContext, { PointLightStruct() })
    var pointLightShadowMapStrategy: PointLightShadowMapStrategy = object: PointLightShadowMapStrategy {
        override fun renderPointLightShadowMaps(renderState: RenderState) {}
        override fun bindTextures() {}
    }
    var areaLightDepthMaps: List<Int> = listOf()
}
