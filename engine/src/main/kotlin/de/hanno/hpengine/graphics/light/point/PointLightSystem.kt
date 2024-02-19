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
import de.hanno.hpengine.artemis.mapEntity
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.buffers.copyTo
import de.hanno.hpengine.buffers.enlarge
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.state.*
import de.hanno.hpengine.graphics.texture.TextureManagerBaseSystem
import de.hanno.hpengine.math.createPerspective
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.transform.EntityMovementSystem
import org.koin.core.annotation.Single
import struktgen.api.forIndex

// TODO: Autoadd Transform
@All(PointLightComponent::class, TransformComponent::class)
@Single(binds = [BaseSystem::class, PointLightSystem::class, RenderSystem::class])
class PointLightSystem(
    graphicsApi: GraphicsApi,
    private val entitiesStateHolder: EntitiesStateHolder,
    pointLightStateHolder: PointLightStateHolder,
    val shadowMapStrategy: CubeShadowMapStrategy,
    private val entityMovementSystem: EntityMovementSystem,
) : BaseEntitySystem(), RenderSystem, Extractor {
    private var gpuPointLights =
        graphicsApi.PersistentShaderStorageBuffer(20 * PointLightStruct.type.sizeInBytes).typed(PointLightStruct.type)
    lateinit var pointLightComponentMapper: ComponentMapper<PointLightComponent>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>

    private val lightState = pointLightStateHolder.lightState

    val pointLightMovedInCycle = mutableMapOf<Int, Long>()

    private val shadowMapsRenderedInCycle = mutableMapOf<Int, Long>()

    override fun render(renderState: RenderState) {
        val entitiesState = renderState[entitiesStateHolder.entitiesState]
        val lightState = renderState[lightState]

        repeat(lightState.pointLightCount) { lightIndex ->
            val renderedInCycle = shadowMapsRenderedInCycle[lightIndex]

            val needsRerender = renderedInCycle?.let { renderedInCycle ->
                val lightHasMoved = (lightState.pointLightMovedInCycle[lightIndex] ?: -1) >= renderedInCycle

                renderedInCycle <= entitiesState.staticEntityMovedInCycle
                        || renderedInCycle <= entitiesState.entityAddedInCycle
                        || !shadowMapsRenderedInCycle.containsKey(lightIndex)
                        || lightHasMoved
            } ?: true

            if (needsRerender) {
                shadowMapStrategy.renderPointLightShadowMap(lightIndex, renderState)
                shadowMapsRenderedInCycle[lightIndex] = renderState.cycle
            }
        }
    }

    override fun extract(currentWriteState: RenderState) {
        currentWriteState[lightState].pointLightMovedInCycle.clear()
        currentWriteState[lightState].pointLightMovedInCycle.putAll(pointLightMovedInCycle)

        currentWriteState[lightState].pointLightBuffer.ensureCapacityInBytes(gpuPointLights.sizeInBytes)
        gpuPointLights.buffer.copyTo(currentWriteState[lightState].pointLightBuffer.buffer)
        currentWriteState[lightState].pointLightCount = mapEntity { 1 }.count()
    }

    companion object {
        @JvmField
        val MAX_POINTLIGHT_SHADOWMAPS = 5
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
                target.shadow = pointLight.shadow
            }
            pointLightMovedInCycle[index] = entityMovementSystem.cycleEntityHasMovedIn(entityId)
            index++
        }

    }
}
