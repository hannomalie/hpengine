package de.hanno.hpengine.engine.graphics.light.point

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.pipelines.safeCopyTo
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.engine.model.instanceCount
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.util.Util
import de.hanno.struct.StructArray
import de.hanno.struct.enlarge
import kotlinx.coroutines.CoroutineScope

class PointLightComponentSystem: SimpleComponentSystem<PointLight>(componentClass = PointLight::class.java)

class PointLightSystem(val engine: EngineContext<OpenGl>,
                       scene: Scene): SimpleEntitySystem(scene, listOf(PointLight::class.java)), RenderSystem {

    private var gpuPointLightArray = StructArray(size = 20) { PointLightStruct() }

    var pointLightMovedInCycle: Long = 0
    private val cameraEntity = Entity("PointLightSystemCameraDummy")
    val camera = Camera(cameraEntity, Util.createPerspective(90f, 1f, 1f, 500f), 1f, 500f, 90f, 1f)

    val shadowMapStrategy = if (engine.config.quality.isUseDpsm) {
            DualParaboloidShadowMapStrategy(engine, this, cameraEntity, scene.entityManager, scene.modelComponentSystem)
        } else {
            CubeShadowMapStrategy(engine, this)
        }

    private fun bufferLights() {
        gpuPointLightArray = gpuPointLightArray.enlarge(getRequiredPointLightBufferSize())
        val pointLights = getComponents(PointLight::class.java)
        for((index, pointLight) in pointLights.withIndex()) {
            val target = gpuPointLightArray.getAtIndex(index)
            target.position.set(pointLight.entity.transform.position)
            target.radius = pointLight.radius
            target.color.set(pointLight.color)
        }
    }

    fun getRequiredPointLightBufferSize() = getComponents(PointLight::class.java).sumBy { it.entity.instanceCount }

    override fun CoroutineScope.update(deltaSeconds: Float) {
        val pointLights = getComponents(PointLight::class.java)

        for (i in 0 until pointLights.size) {
            val pointLight = pointLights[i]
            val pointLightHasMoved = scene.entityManager.run { pointLight.entity.hasMoved }
            if (!pointLightHasMoved) {
                continue
            }
            pointLightMovedInCycle = scene.currentCycle
        }

        val pointLightsIterator = pointLights.iterator()
        while (pointLightsIterator.hasNext()) {
            with(pointLightsIterator.next()) {
                update(deltaSeconds)
            }
        }

        this@PointLightSystem.bufferLights()
    }

    fun getPointLights(): List<PointLight> = getComponents(PointLight::class.java)

    private var shadowMapsRenderedInCycle: Long = -1

    override fun render(result: DrawResult, state: RenderState) {
        val needsRerender = state.pointLightMovedInCycle > shadowMapsRenderedInCycle ||
                state.entitiesState.entityMovedInCycle > shadowMapsRenderedInCycle ||
                state.entitiesState.entityAddedInCycle > shadowMapsRenderedInCycle ||
                state.entitiesState.componentAddedInCycle > shadowMapsRenderedInCycle
        if(needsRerender) {
            shadowMapStrategy.renderPointLightShadowMaps(state)
            shadowMapsRenderedInCycle = state.cycle
        }
    }

    override fun extract(renderState: RenderState) {
        renderState.pointLightMovedInCycle = pointLightMovedInCycle

        renderState.lightState.pointLights = getPointLights()
        gpuPointLightArray.safeCopyTo(renderState.lightState.pointLightBuffer)
        renderState.lightState.pointLightShadowMapStrategy = shadowMapStrategy
    }

    companion object {
        @JvmField val MAX_POINTLIGHT_SHADOWMAPS = 5
    }
}
