package de.hanno.hpengine.graphics.light.point

import PointLightStructImpl.Companion.type
import com.artemis.BaseEntitySystem
import com.artemis.ComponentMapper
import com.artemis.World
import com.artemis.annotations.All

import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.artemis.PointLightComponent
import de.hanno.hpengine.artemis.TransformComponent
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentMappedBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.Transform
import de.hanno.hpengine.artemis.EntitiesStateHolder
import de.hanno.hpengine.buffers.copyTo
import de.hanno.hpengine.graphics.RenderStateContext
import de.hanno.hpengine.graphics.renderer.pipelines.enlarge
import de.hanno.hpengine.graphics.state.*
import de.hanno.hpengine.math.createPerspective

// TODO: Autoadd Transform
context(GpuContext, RenderStateContext)
@All(PointLightComponent::class, TransformComponent::class)
class PointLightSystem(
    config: Config,
    programManager: ProgramManager,
    pointLightStateHolder: PointLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
): BaseEntitySystem(), RenderSystem, Extractor {
    override lateinit var artemisWorld: World
    private var gpuPointLights = PersistentMappedBuffer(20 * PointLightStruct.type.sizeInBytes).typed(PointLightStruct.type)
    lateinit var pointLightComponentMapper: ComponentMapper<PointLightComponent>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>

    private val lightState = pointLightStateHolder.lightState

    var pointLightMovedInCycle: Long = 0
    val camera = Camera(Transform(), createPerspective(90f, 1f, 1f, 500f), 1f, 500f, 90f, 1f)

    val shadowMapStrategy = if (config.quality.isUseDpsm) {
            DualParaboloidShadowMapStrategy(
                this,
                programManager,
                config
            )
        } else {
            CubeShadowMapStrategy(config, programManager)
        }

    private var shadowMapsRenderedInCycle: Long = -1

    override fun render(result: DrawResult, renderState: RenderState) {
        val entitiesState = renderState[entitiesStateHolder.entitiesState]
        val needsRerender = renderState.pointLightMovedInCycle > shadowMapsRenderedInCycle ||
                entitiesState.entityMovedInCycle > shadowMapsRenderedInCycle ||
                entitiesState.entityAddedInCycle > shadowMapsRenderedInCycle ||
                entitiesState.componentAddedInCycle > shadowMapsRenderedInCycle
        if(needsRerender) {
            shadowMapStrategy.renderPointLightShadowMaps(renderState)
            shadowMapsRenderedInCycle = renderState.cycle
        }
    }

    override fun extract(currentWriteState: RenderState) {
        currentWriteState.pointLightMovedInCycle = pointLightMovedInCycle

        currentWriteState[lightState].pointLightBuffer.resize(gpuPointLights.sizeInBytes)
        gpuPointLights.buffer.copyTo(currentWriteState[lightState].pointLightBuffer.buffer)
        currentWriteState[lightState].pointLightShadowMapStrategy = shadowMapStrategy
    }

    companion object {
        @JvmField val MAX_POINTLIGHT_SHADOWMAPS = 5
    }

    override fun processSystem() {
        var pointLightCount = 0
        forEachEntity { entityId ->
            pointLightCount++
        }
        gpuPointLights.typedBuffer.enlarge(pointLightCount)

        var index = 0
        forEachEntity { entityId ->
            val transform = transformComponentMapper[entityId].transform
            val pointLight = pointLightComponentMapper[entityId]

            gpuPointLights.typedBuffer.forIndex(index) { target ->
                target.position.set(transform.position)
                target.radius = pointLight.radius
                target.color.set(pointLight.color)
            }
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
