package de.hanno.hpengine.engine.graphics.light

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.InputComponentSystem
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.event.LightChangedEvent
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.light.arealight.AreaLight
import de.hanno.hpengine.engine.graphics.light.pointlight.PointLight
import de.hanno.hpengine.engine.manager.Manager
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.concurrent.CopyOnWriteArrayList

class LightManager(private val engine: Engine, val eventBus: EventBus, inputControllerSystem: InputComponentSystem): Manager {

    var tubeLights: MutableList<TubeLight> = CopyOnWriteArrayList()
    var directionalLight = DirectionalLight(Entity())
    //                    .apply { addComponent(cameraComponentSystem.create(this, Util.createOrthogonal(-1000f, 1000f, 1000f, -1000f, -2500f, 2500f), -2500f, 2500f, 60f, 16f / 9f)) }

    private val directionalLightComponent: DirectionalLight

    var directionalLightMovedInCycle: Long = 0

    init {
        eventBus.register(this)
        directionalLightComponent = createDirectionalLight(directionalLight.getEntity())
        directionalLight.getEntity().addComponent(directionalLightComponent)
        inputControllerSystem.addComponent(directionalLight.addInputController(this.engine))
    }

    fun getPointLight(entity: Entity, range: Float): PointLight {
        return getPointLight(entity, Vector4f(1f, 1f, 1f, 1f), range)
    }

    @JvmOverloads
    fun getPointLight(entity: Entity, colorIntensity: Vector4f = Vector4f(1f, 1f, 1f, 1f), range: Float = PointLight.DEFAULT_RANGE): PointLight {
        val light = PointLight(entity, colorIntensity, range)
        entity.addComponent(light)
        return light
    }

    fun getTubeLight(entity: Entity, length: Float, radius: Float): TubeLight {
        val tubeLight = TubeLight(entity, Vector3f(1f, 1f, 1f), length, radius)
        entity.addComponent(tubeLight)
        return tubeLight
    }

    fun getAreaLight(entity: Entity, color: Vector3f = Vector3f(1f, 1f, 1f), width: Int = 100, height: Int = 20, range: Int = 20): AreaLight {
        val areaLight = AreaLight(entity, color, Vector3f(width.toFloat(), height.toFloat(), range.toFloat()))
        entity.addComponent(areaLight)
        return areaLight
    }

    fun update(deltaSeconds: Float, currentCycle: Long) {

        directionalLight.update(deltaSeconds)

        if (directionalLight.getEntity().hasMoved()) {
            directionalLightMovedInCycle = currentCycle
            directionalLight.entity.isHasMoved = false
        }
    }

    fun createDirectionalLight(entity: Entity): DirectionalLight {
        return DirectionalLight(entity)
    }
    inline fun <reified T> addLight(light: T) {
        if (light is TubeLight) {
            tubeLights.add(light)
        }
        eventBus.post(LightChangedEvent())
    }
    override fun clear() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
    companion object {

        @JvmField
        var MAX_AREALIGHT_SHADOWMAPS = 2
        @JvmField
        var MAX_POINTLIGHT_SHADOWMAPS = 5
        @JvmField
        var AREALIGHT_SHADOWMAP_RESOLUTION = 512
    }
}
