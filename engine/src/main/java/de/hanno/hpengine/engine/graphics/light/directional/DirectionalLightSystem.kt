package de.hanno.hpengine.engine.graphics.light.directional

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.OpenGlBackend
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DirectionalLightShadowMapExtension
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.scene.SimpleScene
import de.hanno.struct.copyTo

class DirectionalLightSystem(val _engine: Engine<OpenGlBackend>, simpleScene: SimpleScene, val eventBus: EventBus): SimpleEntitySystem(_engine, simpleScene, listOf(DirectionalLight::class.java)), RenderSystem {
    var directionalLightMovedInCycle: Long = 0

    private var shadowMapExtension: DirectionalLightShadowMapExtension

    init {
        eventBus.register(this)
        shadowMapExtension = DirectionalLightShadowMapExtension(engine)
    }

    override fun update(deltaSeconds: Float) {

        getDirectionalLight().update(deltaSeconds)

        if (getDirectionalLight().getEntity().hasMoved()) {
            directionalLightMovedInCycle = engine.scene.currentCycle
            getDirectionalLight().entity.isHasMoved = false
        }
    }

    fun getDirectionalLight() = getComponents(DirectionalLight::class.java).first()

    override fun extract(renderState: RenderState) {
        renderState.directionalLightHasMovedInCycle = directionalLightMovedInCycle

        with(getDirectionalLight()) {
            renderState.directionalLightState.color.set(color)
            renderState.directionalLightState.direction.set(direction)
            renderState.directionalLightState.scatterFactor = scatterFactor
            renderState.directionalLightState.viewMatrix.set(viewMatrix)
            renderState.directionalLightState.projectionMatrix.set(projectionMatrix)
            renderState.directionalLightState.viewProjectionMatrix.set(viewProjectionMatrix)
            renderState.directionalLightState.shadowMapHandle = shadowMapExtension.renderTarget.renderedTextureHandles[0]
            renderState.directionalLightState.shadowMapId = shadowMapExtension.renderTarget.renderedTextures[0]
        }

        renderState.directionalLightState.buffer.copyTo(renderState.directionalLightBuffer.buffer)
    }
    override fun render(result: DrawResult, state: RenderState) {
        shadowMapExtension.renderFirstPass(_engine.backend, _engine.gpuContext, result.firstPassResult, state)
    }
}
