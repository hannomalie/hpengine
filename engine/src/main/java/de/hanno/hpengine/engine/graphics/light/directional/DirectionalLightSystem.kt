package de.hanno.hpengine.engine.graphics.light.directional

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.scene.Scene
import kotlinx.coroutines.CoroutineScope

class DirectionalLightSystem(val engine: EngineContext,
                             scene: Scene,
                             val eventBus: EventBus): SimpleEntitySystem(scene, listOf(DirectionalLight::class.java)), RenderSystem {
    var directionalLightMovedInCycle: Long = 0

    init {
        eventBus.register(this)
    }

    override fun CoroutineScope.update(deltaSeconds: Float) {
        val light = getDirectionalLight() ?: return

        with(light) {
            update(deltaSeconds)
        }
        if(scene.entityManager.run { light.entity.hasMoved }) {
            this@DirectionalLightSystem.directionalLightMovedInCycle = scene.currentCycle
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
        }
    }
}
