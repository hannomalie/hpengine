package de.hanno.hpengine.engine.graphics.light.directional

import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.scene.Scene
import org.koin.core.component.get

class DirectionalLightRenderSystem: RenderSystem {

    override fun extract(scene: Scene, renderState: RenderState) {
        val directionalLightSystem = scene.get<DirectionalLightSystem>()

        renderState.directionalLightHasMovedInCycle = directionalLightSystem.directionalLightMovedInCycle
        val light = directionalLightSystem.getDirectionalLight() ?: return

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
class DirectionalLightSystem: SimpleEntitySystem(listOf(DirectionalLight::class.java)) {
    var directionalLightMovedInCycle: Long = 0

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        val light = getDirectionalLight() ?: return

        light.update(scene, deltaSeconds)

        if(scene.entityManager.run { light.entity.hasMoved }) {
            scene.currentCycle.also { directionalLightMovedInCycle = it }
        }
    }

    fun getDirectionalLight() = getComponents(DirectionalLight::class.java).firstOrNull()

}
