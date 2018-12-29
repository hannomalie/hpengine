package de.hanno.hpengine.engine.graphics.state

import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.engine.graphics.light.area.AreaLight
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.light.point.PointLightShadowMapStrategy
import de.hanno.hpengine.engine.graphics.light.tube.TubeLight

class LightState(gpuContext: GpuContext) {
    var pointLights: List<PointLight> = listOf()
    var pointLightBuffer = PersistentMappedBuffer(gpuContext, 8000)
    var areaLights: List<AreaLight> = listOf()
    var tubeLights: List<TubeLight> = listOf()
    var pointLightShadowMapStrategy: PointLightShadowMapStrategy = object: PointLightShadowMapStrategy {
        override fun renderPointLightShadowMaps(renderState: RenderState) {}
        override fun bindTextures() {}
    }
    var areaLightDepthMaps: List<Int> = listOf()
}
