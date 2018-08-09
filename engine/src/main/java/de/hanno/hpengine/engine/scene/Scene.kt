package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.EntityManager
import de.hanno.hpengine.engine.entity.EntitySystemRegistry
import de.hanno.hpengine.engine.graphics.light.area.AreaLight
import de.hanno.hpengine.engine.graphics.light.area.AreaLightComponentSystem
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLightSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.light.point.PointLightComponentSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.light.tube.TubeLight
import de.hanno.hpengine.engine.graphics.light.tube.TubeLightComponentSystem
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.lifecycle.LifeCycle
import de.hanno.hpengine.engine.manager.ComponentSystemRegistry
import de.hanno.hpengine.engine.manager.ManagerRegistry
import de.hanno.hpengine.engine.model.material.MaterialManager
import java.io.Serializable
import java.util.*

interface Scene : LifeCycle, Serializable {
    val name: String
    val camera: Camera
    var activeCamera: Camera
    var currentCycle: Long
    val managers: ManagerRegistry
    val entityManager: EntityManager
    val entitySystems: EntitySystemRegistry
    val componentSystems: ComponentSystemRegistry
    val renderSystems: List<RenderSystem>

    val materialManager: MaterialManager
    val environmentProbeManager: EnvironmentProbeManager //TODO: Remove this from interface

    var isInitiallyDrawn: Boolean
    val minMax: AABB

    fun clear() {
        componentSystems.clearSystems()
        entitySystems.clearSystems()
        entityManager.clear()
    }
    fun extract(currentWriteState: RenderState)

    fun getEntities() = entityManager.getEntities()
    fun addAll(entities: List<Entity>) {
        entityManager.add(entities)

        entitySystems.onEntityAdded(entities)
        componentSystems.onEntityAdded(entities)
        managers.onEntityAdded(entities)

        minMax.calculateMinMax(entityManager.getEntities())

        entityManager.entityAddedInCycle = currentCycle
    }

    fun getPointLights(): List<PointLight> = componentSystems.get(PointLightComponentSystem::class.java).getComponents()
    fun getTubeLights(): List<TubeLight> = componentSystems.get(TubeLightComponentSystem::class.java).getComponents()
    fun getAreaLights(): List<AreaLight> = componentSystems.get(AreaLightComponentSystem::class.java).getComponents()
    fun getAreaLightSystem(): AreaLightSystem = entitySystems.get(AreaLightSystem::class.java)
    fun getPointLightSystem(): PointLightSystem = entitySystems.get(PointLightSystem::class.java)
    fun add(entity: Entity) = addAll(listOf(entity))
    fun getEntity(name: String): Optional<Entity> {
        val candidate = entityManager.getEntities().find { e -> e.name == name }
        return Optional.ofNullable(candidate)
    }
    fun restoreWorldCamera()
    override fun update(deltaSeconds: Float) {
        managers.update(deltaSeconds)
        componentSystems.update(deltaSeconds)
        entitySystems.update(deltaSeconds)

        managers.afterUpdate(deltaSeconds)
    }

}