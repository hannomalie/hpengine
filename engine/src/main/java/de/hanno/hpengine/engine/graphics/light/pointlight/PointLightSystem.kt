package de.hanno.hpengine.engine.graphics.light.pointlight

import com.google.common.eventbus.Subscribe
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.event.LightChangedEvent
import de.hanno.hpengine.engine.event.PointLightMovedEvent
import de.hanno.hpengine.engine.event.SceneInitEvent
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.StateConsumer
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.util.Util
import net.engio.mbassy.listener.Handler
import org.lwjgl.BufferUtils
import java.nio.FloatBuffer

class PointLightSystem(engine: Engine, scene: Scene): SimpleEntitySystem(engine, scene, listOf(PointLight::class.java)), StateConsumer {

    var pointLightMovedInCycle: Long = 0
    private val cameraEntity = Entity()
    val camera = Camera(cameraEntity, Util.createPerspective(90f, 1f, 1f, 500f), 1f, 500f, 90f, 1f)

    private val pointLightsForwardMaxCount = 20
    private var pointLightPositions = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount * 3)
    var pointLightColors = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount * 3)
    var pointLightRadiuses = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount)

    val lightBuffer: GPUBuffer<PointLight> = engine.gpuContext.calculate { PersistentMappedBuffer<PointLight>(engine.gpuContext, 1000) }

    val shadowMapStrategy = if (Config.getInstance().isUseDpsm) {
        DualParaboloidShadowMapStrategy(engine, this, cameraEntity)
        } else {
        CubeShadowMapStrategy(engine, this, cameraEntity)
        }

    private fun bufferLights() {
        val pointLights = getComponents(PointLight::class.java)
        engine.gpuContext.execute {
            if (pointLights.isNotEmpty()) {
                lightBuffer.put(*Util.toArray<PointLight>(pointLights, PointLight::class.java))
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

    fun getPointLightPositions(): FloatBuffer {
        updatePointLightArrays()
        return pointLightPositions
    }

    private fun updatePointLightArrays() {
        val positions = FloatArray(pointLightsForwardMaxCount * 3)
        val colors = FloatArray(pointLightsForwardMaxCount * 3)
        val radiuses = FloatArray(pointLightsForwardMaxCount)

        val pointLights = getComponents(PointLight::class.java)
        for (i in 0 until Math.min(pointLightsForwardMaxCount, pointLights.size)) {
            val light = pointLights[i]
            positions[3 * i] = light.entity.position.x
            positions[3 * i + 1] = light.entity.position.y
            positions[3 * i + 2] = light.entity.position.z

            colors[3 * i] = light.color.x
            colors[3 * i + 1] = light.color.y
            colors[3 * i + 2] = light.color.z

            radiuses[i] = light.radius
        }

        pointLightPositions = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount * 3)
        pointLightPositions.put(positions)
        pointLightPositions.rewind()
        pointLightColors = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount * 3)
        pointLightColors.put(colors)
        pointLightColors.rewind()
        pointLightRadiuses = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount)
        pointLightRadiuses.put(radiuses)
        pointLightRadiuses.rewind()
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

    override fun consume(state: RenderState) {
        if(state.entityWasAdded() || state.pointLightHasMoved()) {
            shadowMapStrategy.renderPointLightShadowMaps(state)
        }
    }
}
