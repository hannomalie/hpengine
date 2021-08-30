package de.hanno.hpengine.engine.graphics.light.directional

import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.scene.Scene
import org.koin.core.component.get

class DirectionalLightSystem: SimpleEntitySystem(listOf(DirectionalLight::class.java)) {
    var directionalLightMovedInCycle: Long = 0

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        val light = getDirectionalLight() ?: return

        light.update(scene, deltaSeconds)

        if(scene.entityManager.run { light._entity.hasMoved }) {
            scene.currentCycle.also { directionalLightMovedInCycle = it }
        }
    }

    override fun extract(renderState: RenderState) {
        renderState.directionalLightHasMovedInCycle = this.directionalLightMovedInCycle

        renderState.directionalLightHasMovedInCycle = this.directionalLightMovedInCycle
        val light = getDirectionalLight() ?: return

        with(light) {
            val directionalLightState = renderState.directionalLightState[0]

            directionalLightState.color.set(color)
            directionalLightState.direction.set(direction)
            directionalLightState.scatterFactor = scatterFactor
            directionalLightState.viewMatrix.set(viewMatrix)
            directionalLightState.projectionMatrix.set(projectionMatrix)
            directionalLightState.viewProjectionMatrix.set(viewProjectionMatrix)
        }

    }
    fun getDirectionalLight() = getComponents(DirectionalLight::class.java).firstOrNull()

}
