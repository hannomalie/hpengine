package de.hanno.hpengine.engine.graphics.light.point

import com.artemis.World
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.pipelines.safeCopyTo
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.instancing.instanceCount
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.util.Util
import de.hanno.struct.StructArray
import de.hanno.struct.enlarge

class PointLightComponentSystem: SimpleComponentSystem<PointLight>(componentClass = PointLight::class.java)

class PointLightSystem(
    config: Config, programManager: ProgramManager<OpenGl>, gpuContext: GpuContext<OpenGl>
): SimpleEntitySystem(listOf(PointLight::class.java)), RenderSystem {
    override lateinit var artemisWorld: World
    private var gpuPointLightArray = StructArray(size = 20) { PointLightStruct() }

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
            CubeShadowMapStrategy(this, config, gpuContext, programManager)
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

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        val pointLights = this@PointLightSystem.getComponents(PointLight::class.java)

        for (i in 0 until pointLights.size) {
            val pointLight = pointLights[i]
            val pointLightHasMoved = scene.entityManager.run { pointLight.entity.hasMoved }
            if (!pointLightHasMoved) {
                continue
            }
            this@PointLightSystem.pointLightMovedInCycle = scene.currentCycle
        }

        val pointLightsIterator = pointLights.iterator()
        while (pointLightsIterator.hasNext()) {
            with(pointLightsIterator.next()) {
                update(scene, deltaSeconds)
            }
        }

        this@PointLightSystem.bufferLights()
    }

    fun getPointLights(): List<PointLight> = getComponents(PointLight::class.java)

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
