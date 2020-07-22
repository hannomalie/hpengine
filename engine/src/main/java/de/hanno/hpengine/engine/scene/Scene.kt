package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.EntityManager
import de.hanno.hpengine.engine.entity.EntitySystemRegistry
import de.hanno.hpengine.engine.graphics.light.area.AreaLight
import de.hanno.hpengine.engine.graphics.light.area.AreaLightComponentSystem
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.light.point.PointLightComponentSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.light.tube.TubeLight
import de.hanno.hpengine.engine.graphics.light.tube.TubeLightComponentSystem
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.manager.ComponentSystemRegistry
import de.hanno.hpengine.engine.manager.ManagerRegistry
import de.hanno.hpengine.engine.model.ModelComponentSystem
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.texture.CubeMap
import de.hanno.hpengine.engine.transform.AABB
import kotlinx.coroutines.CoroutineScope
import java.io.Serializable
import java.util.Optional

interface Scene : Updatable, Serializable {
    val name: String
    val camera: Camera
    var activeCamera: Camera
    var currentCycle: Long
    val managers: ManagerRegistry
    val entityManager: EntityManager
    val entitySystems: EntitySystemRegistry
    val componentSystems: ComponentSystemRegistry

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
        with(entityManager) { add(entities) }

        with(entitySystems) { onEntityAdded(entities) }
        with(componentSystems) { onEntityAdded(entities) }
        with(managers) { onEntityAdded(entities) }

        calculateMinMax()

        // TODO: This is not too correct but the cycle counter gets updated just before this happens
        entityManager.entityAddedInCycle = currentCycle-1
    }

    fun onComponentAdded(component: Component) {
        with(componentSystems) { onComponentAdded(component) }
        with(managers) { onComponentAdded(component) }
        with(entitySystems) { onComponentAdded(component) }

        // TODO: This is not too correct but the cycle counter gets updated just before this happens
        entityManager.componentAddedInCycle = currentCycle-1
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

    @JvmDefault
    override fun CoroutineScope.update(deltaSeconds: Float) {
        with(managers) {
            update(deltaSeconds)
        }
        with(componentSystems) {
            update(deltaSeconds)
        }
        with(entitySystems) {
            update(deltaSeconds)
        }
    }

    @JvmDefault
    override fun CoroutineScope.afterUpdate(deltaSeconds: Float) {
        with(managers) {
            afterUpdate(deltaSeconds)
        }
    }

    fun addComponent(selection: Entity, component: Component) {
        selection.addComponent(component)
        onComponentAdded(component)
    }

    fun calculateMinMax() {
        minMax.localAABB = minMax.calculateMinMax(entityManager.getEntities())
    }

    var initialized: Boolean
    val modelComponentSystem: ModelComponentSystem
}


class SkyBox(var cubeMap: CubeMap)