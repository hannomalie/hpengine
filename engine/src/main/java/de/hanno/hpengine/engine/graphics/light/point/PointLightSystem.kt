package de.hanno.hpengine.engine.graphics.light.point

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.OpenGlBackend
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.event.PointLightMovedEvent
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.engine.model.instanceCount
import de.hanno.hpengine.engine.scene.SimpleScene
import de.hanno.hpengine.util.Util
import de.hanno.struct.StructArray
import de.hanno.struct.copyTo
import de.hanno.struct.enlarge
import org.joml.Vector4f

class PointLightComponentSystem: SimpleComponentSystem<PointLight>(componentClass = PointLight::class.java, factory = { PointLight(it, Vector4f(1f,1f,1f,1f), 100f) })

class PointLightSystem(engine: Engine<OpenGlBackend>, simpleScene: SimpleScene): SimpleEntitySystem(engine, simpleScene, listOf(PointLight::class.java)), RenderSystem {

    private var gpuPointLightArray = StructArray(size = 20) { PointLightStruct() }

    var pointLightMovedInCycle: Long = 0
    private val cameraEntity = Entity("PointLightSystemCameraDummy")
    val camera = Camera(cameraEntity, Util.createPerspective(90f, 1f, 1f, 500f), 1f, 500f, 90f, 1f)

//    TODO: This has to be part of a custom renderstate!
    val lightBuffer: PersistentMappedBuffer = engine.gpuContext.calculate { PersistentMappedBuffer(engine.gpuContext, 1000) }

    val shadowMapStrategy = if (Config.getInstance().isUseDpsm) {
            DualParaboloidShadowMapStrategy(engine, this, cameraEntity)
        } else {
            CubeShadowMapStrategy(engine, this)
        }

    private fun bufferLights() {
        gpuPointLightArray = gpuPointLightArray.enlarge(getRequiredPointLightBufferSize())
        val pointLights = getComponents(PointLight::class.java)
        for((index, pointLight) in pointLights.withIndex()) {
            val target = gpuPointLightArray.getAtIndex(index)
            target.position.set(pointLight.entity.position)
            target.radius = pointLight.radius
            target.color.set(pointLight.color)
        }
        lightBuffer.setCapacityInBytes(pointLights.size * PointLightStruct.getBytesPerInstance())
        lightBuffer.buffer.rewind()
        gpuPointLightArray.copyTo(lightBuffer.buffer)
        lightBuffer.buffer.rewind()
    }

    fun getRequiredPointLightBufferSize() = getComponents(PointLight::class.java).sumBy { it.entity.instanceCount }

    override fun update(deltaSeconds: Float) {
        val pointLights = getComponents(PointLight::class.java)

        for (i in 0 until pointLights.size) {
            val pointLight = pointLights[i]
            if (!pointLight.entity.hasMoved()) {
                continue
            }
            pointLightMovedInCycle = engine.renderManager.drawCycle.get()
            engine.eventBus.post(PointLightMovedEvent())
            pointLight.entity.isHasMoved = false
        }

        val pointLightsIterator = pointLights.iterator()
        while (pointLightsIterator.hasNext()) {
            pointLightsIterator.next().update(deltaSeconds)
        }

        bufferLights()
    }

    fun getPointLights(): List<PointLight> = getComponents(PointLight::class.java)

    private var shadowMapsRenderedInCycle: Long = -1

    override fun render(result: DrawResult, state: RenderState) {
        if(state.entityWasAdded() || state.pointLightMovedInCycle > shadowMapsRenderedInCycle || state.entitiesState.entityMovedInCycle > shadowMapsRenderedInCycle) {
            shadowMapsRenderedInCycle = state.cycle
            shadowMapStrategy.renderPointLightShadowMaps(state)
        }
    }

    override fun extract(renderState: RenderState) {
        renderState.pointLightMovedInCycle = pointLightMovedInCycle

        renderState.lightState.pointLights = getPointLights()
        renderState.lightState.pointLightBuffer = lightBuffer
        renderState.lightState.pointLightShadowMapStrategy = shadowMapStrategy
    }

    companion object {
        @JvmField val MAX_POINTLIGHT_SHADOWMAPS = 5
    }
}
