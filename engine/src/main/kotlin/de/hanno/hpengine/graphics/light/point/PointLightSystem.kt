package de.hanno.hpengine.graphics.light.point

import PointLightStructImpl.Companion.type
import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.All

import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.buffer.typed
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.Transform
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.buffers.copyTo
import de.hanno.hpengine.buffers.enlarge
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.state.*
import de.hanno.hpengine.math.createPerspective
import org.koin.core.annotation.Single
import struktgen.api.forIndex

// TODO: Autoadd Transform
@All(PointLightComponent::class, TransformComponent::class)
@Single(binds = [BaseSystem::class, PointLightSystem::class])
class PointLightSystem(
    private val graphicsApi: GraphicsApi,
    private val renderStateContext: RenderStateContext,
    config: Config,
    programManager: ProgramManager,
    pointLightStateHolder: PointLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
): BaseEntitySystem(), RenderSystem, Extractor {
    private var gpuPointLights = graphicsApi.PersistentShaderStorageBuffer(20 * PointLightStruct.type.sizeInBytes).typed(PointLightStruct.type)
    lateinit var pointLightComponentMapper: ComponentMapper<PointLightComponent>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>

    private val lightState = pointLightStateHolder.lightState

    var pointLightMovedInCycle: Long = 0
    val camera = Camera(Transform()).apply {
        near = 1f
        far = 500f
        fov = 90f
        ratio = 1f
    }

    val shadowMapStrategy = if (config.quality.isUseDpsm) {
            DualParaboloidShadowMapStrategy(
                graphicsApi,
                this,
                programManager,
                config
            )
        } else {
            CubeShadowMapStrategy(graphicsApi, config, programManager)
        }

    private var shadowMapsRenderedInCycle: Long = -1

    override fun render(renderState: RenderState) {
        val entitiesState = renderState[entitiesStateHolder.entitiesState]
        val needsRerender = renderState[lightState].pointLightMovedInCycle > shadowMapsRenderedInCycle ||
                entitiesState.entityMovedInCycle > shadowMapsRenderedInCycle ||
                entitiesState.entityAddedInCycle > shadowMapsRenderedInCycle ||
                entitiesState.componentAddedInCycle > shadowMapsRenderedInCycle
        if(needsRerender) {
            shadowMapStrategy.renderPointLightShadowMaps(renderState)
            shadowMapsRenderedInCycle = renderState.cycle
        }
    }

    override fun extract(currentWriteState: RenderState) {
        currentWriteState[lightState].pointLightMovedInCycle = pointLightMovedInCycle

        currentWriteState[lightState].pointLightBuffer.ensureCapacityInBytes(gpuPointLights.sizeInBytes)
        gpuPointLights.buffer.copyTo(currentWriteState[lightState].pointLightBuffer.buffer)
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
