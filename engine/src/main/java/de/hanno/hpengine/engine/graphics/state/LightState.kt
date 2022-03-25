package de.hanno.hpengine.engine.graphics.state

import de.hanno.hpengine.engine.component.artemis.PointLightComponent
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.light.area.AreaLight
import de.hanno.hpengine.engine.graphics.light.point.PointLightShadowMapStrategy
import de.hanno.hpengine.engine.graphics.light.point.PointLightStruct
import de.hanno.hpengine.engine.graphics.light.tube.TubeLight
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer

class LightState(gpuContext: GpuContext<*>) {
    var pointLights: List<PointLightComponent> = listOf()
    var pointLightBuffer = PersistentMappedStructBuffer(0, gpuContext, { PointLightStruct() })
    var areaLights: List<AreaLight> = listOf()
    var tubeLights: List<TubeLight> = listOf()
    var pointLightShadowMapStrategy: PointLightShadowMapStrategy = object: PointLightShadowMapStrategy {
        override fun renderPointLightShadowMaps(renderState: RenderState) {}
        override fun bindTextures() {}
    }
    var areaLightDepthMaps: List<Int> = listOf()
}
