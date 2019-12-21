package de.hanno.hpengine.engine.graphics.light.directional

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DirectionalLightShadowMapExtension
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.scene.Scene
import kotlinx.coroutines.CoroutineScope

class DirectionalLightSystem(val engine: EngineContext<OpenGl>,
                             scene: Scene,
                             val eventBus: EventBus): SimpleEntitySystem(scene, listOf(DirectionalLight::class.java)), RenderSystem {
    var directionalLightMovedInCycle: Long = 0

    private var shadowMapExtension: DirectionalLightShadowMapExtension

    init {
        eventBus.register(this)
        shadowMapExtension = DirectionalLightShadowMapExtension(engine)
    }

    override fun CoroutineScope.update(deltaSeconds: Float) {
        val light = getDirectionalLight() ?: return

        with(light) {
            update(deltaSeconds)
        }
        if (light.entity.hasMoved()) {
            this@DirectionalLightSystem.directionalLightMovedInCycle = scene.currentCycle
            light.entity.isHasMoved = false
        }
    }

    fun getDirectionalLight() = getComponents(DirectionalLight::class.java).firstOrNull()

    override fun extract(renderState: RenderState) {
        renderState.directionalLightHasMovedInCycle = directionalLightMovedInCycle
        val light = getDirectionalLight() ?: return

        with(light) {
            renderState.directionalLightState[0].color.set(color)
            renderState.directionalLightState[0].direction.set(direction)
            renderState.directionalLightState[0].scatterFactor = scatterFactor
            renderState.directionalLightState[0].viewMatrix.set(viewMatrix)
            renderState.directionalLightState[0].projectionMatrix.set(projectionMatrix)
            renderState.directionalLightState[0].viewProjectionMatrix.set(viewProjectionMatrix)
            renderState.directionalLightState[0].shadowMapHandle = shadowMapExtension.renderTarget.renderedTextureHandles[0]
            renderState.directionalLightState[0].shadowMapId = shadowMapExtension.renderTarget.renderedTextures[0]
        }
    }
    override fun render(result: DrawResult, state: RenderState) {
        shadowMapExtension.renderFirstPass(engine.backend, engine.gpuContext, result.firstPassResult, state)
    }
}
