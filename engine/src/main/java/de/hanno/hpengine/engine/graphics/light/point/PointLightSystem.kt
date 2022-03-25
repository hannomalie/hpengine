package de.hanno.hpengine.engine.graphics.light.point

import com.artemis.BaseEntitySystem
import com.artemis.ComponentMapper
import com.artemis.World
import com.artemis.annotations.All
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.artemis.PointLightComponent
import de.hanno.hpengine.engine.component.artemis.TransformComponent
import de.hanno.hpengine.engine.component.artemis.forEachEntity
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.pipelines.safeCopyTo
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.util.Util
import de.hanno.struct.StructArray
import de.hanno.struct.enlarge

// TODO: Autoadd Transform
@All(PointLightComponent::class, TransformComponent::class)
class PointLightSystem(
    config: Config, programManager: ProgramManager<OpenGl>,
    gpuContext: GpuContext<OpenGl>
): BaseEntitySystem(), RenderSystem {
    override lateinit var artemisWorld: World
    private var gpuPointLightArray = StructArray(size = 20) { PointLightStruct() }
    lateinit var pointLightComponentMapper: ComponentMapper<PointLightComponent>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>

    var pointLightMovedInCycle: Long = 0
    private val cameraEntity = Entity("PointLightSystemCameraDummy")
    val camera = Camera(cameraEntity, Util.createPerspective(90f, 1f, 1f, 500f), 1f, 500f, 90f, 1f)

    val shadowMapStrategy = if (config.quality.isUseDpsm) {
            DualParaboloidShadowMapStrategy(
                this,
                programManager,
                gpuContext,
                config
            )
        } else {
            CubeShadowMapStrategy(config, gpuContext, programManager)
        }

    private var shadowMapsRenderedInCycle: Long = -1

    override fun render(result: DrawResult, renderState: RenderState) {
        val needsRerender = renderState.pointLightMovedInCycle > shadowMapsRenderedInCycle ||
                renderState.entitiesState.entityMovedInCycle > shadowMapsRenderedInCycle ||
                renderState.entitiesState.entityAddedInCycle > shadowMapsRenderedInCycle ||
                renderState.entitiesState.componentAddedInCycle > shadowMapsRenderedInCycle
        if(needsRerender) {
            shadowMapStrategy.renderPointLightShadowMaps(renderState)
            shadowMapsRenderedInCycle = renderState.cycle
        }
    }

    fun extract(renderState: RenderState) {
        renderState.pointLightMovedInCycle = pointLightMovedInCycle

        gpuPointLightArray.safeCopyTo(renderState.lightState.pointLightBuffer)
        renderState.lightState.pointLightShadowMapStrategy = shadowMapStrategy
    }

    companion object {
        @JvmField val MAX_POINTLIGHT_SHADOWMAPS = 5
    }

    override fun processSystem() {
        var pointLightCount = 0
        forEachEntity { entityId ->
            pointLightCount++
        }
        gpuPointLightArray = gpuPointLightArray.enlarge(pointLightCount)

        var index = 0
        forEachEntity { entityId ->
            val transform = transformComponentMapper[entityId].transform
            val pointLight = pointLightComponentMapper[entityId]

            val target = gpuPointLightArray.getAtIndex(index)
            target.position.set(transform.position)
            target.radius = pointLight.radius
            target.color.set(pointLight.color)
            index++
        }

        // TODO: Reimplement
//        for (i in 0 until pointLights.size) {
//            val pointLight = pointLights[i]
//            val pointLightHasMoved = scene.entityManager.run { pointLight.entity.hasMoved }
//            if (!pointLightHasMoved) {
//                continue
//            }
//            this@PointLightSystem.pointLightMovedInCycle = scene.currentCycle
//        }

    }
}
