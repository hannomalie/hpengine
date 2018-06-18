package de.hanno.hpengine.engine.graphics.light.point

import com.google.common.eventbus.Subscribe
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.event.LightChangedEvent
import de.hanno.hpengine.engine.event.PointLightMovedEvent
import de.hanno.hpengine.engine.event.SceneInitEvent
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.util.Util
import net.engio.mbassy.listener.Handler
import org.joml.Vector4f

class PointLightComponentSystem: SimpleComponentSystem<PointLight>(theComponentClass = PointLight::class.java, factory = { PointLight(it, Vector4f(1f,1f,1f,1f), 100f) })

class PointLightSystem(engine: Engine, scene: Scene): SimpleEntitySystem(engine, scene, listOf(PointLight::class.java)), RenderSystem {

    var pointLightMovedInCycle: Long = 0
    private val cameraEntity = Entity("PointLightSystemCameraDummy")
    val camera = Camera(cameraEntity, Util.createPerspective(90f, 1f, 1f, 500f), 1f, 500f, 90f, 1f)

    val lightBuffer: PersistentMappedBuffer<PointLight> = engine.gpuContext.calculate { PersistentMappedBuffer(engine.gpuContext, 1000) }

    val shadowMapStrategy = if (Config.getInstance().isUseDpsm) {
        DualParaboloidShadowMapStrategy(engine, this, cameraEntity)
        } else {
        CubeShadowMapStrategy(engine, this)
        }

    private fun bufferLights() {
        val pointLights = getComponents(PointLight::class.java)
        engine.gpuContext.execute {
            if (pointLights.isNotEmpty()) {
                lightBuffer.put(0, pointLights)
            }
        }
    }

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
    }

    @Subscribe
    @Handler
    fun bufferLights(event: LightChangedEvent) {
        bufferLights()
    }

    @Subscribe
    @Handler
    fun bufferLights(event: PointLightMovedEvent) {
        bufferLights()
    }

    @Subscribe
    @Handler
    fun bufferLights(event: SceneInitEvent) {
        bufferLights()
    }

    fun getPointLights(): List<PointLight> = getComponents(PointLight::class.java)

    override fun render(result: DrawResult, state: RenderState) {
        if(state.entityWasAdded() || state.pointLightHasMoved()) {
            shadowMapStrategy.renderPointLightShadowMaps(state)
        }
    }

    companion object {
        @JvmField val MAX_POINTLIGHT_SHADOWMAPS = 5
    }
}
