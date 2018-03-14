package de.hanno.hpengine.engine.graphics.light.directional

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.light.area.AreaLight
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.light.tube.TubeLight
import de.hanno.hpengine.engine.scene.Scene
import org.joml.Vector3f
import org.joml.Vector4f

class DirectionalLightSystem(engine: Engine, scene: Scene, val eventBus: EventBus): SimpleEntitySystem(engine, scene, listOf(DirectionalLight::class.java)) {

    var directionalLightMovedInCycle: Long = 0

    init {
        eventBus.register(this)
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

    override fun update(deltaSeconds: Float) {

        getDirectionalLight().update(deltaSeconds)

        if (getDirectionalLight().getEntity().hasMoved()) {
            directionalLightMovedInCycle = engine.getScene().currentRenderCycle
            getDirectionalLight().entity.isHasMoved = false
        }
    }

    companion object {
        @JvmField
        var MAX_AREALIGHT_SHADOWMAPS = 2
        @JvmField
        var MAX_POINTLIGHT_SHADOWMAPS = 5
        @JvmField
        var AREALIGHT_SHADOWMAP_RESOLUTION = 512
    }

    fun getDirectionalLight() = getComponents(DirectionalLight::class.java).first()
}
