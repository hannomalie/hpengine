package de.hanno.hpengine.engine.entity

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.container.EntityContainer
import de.hanno.hpengine.engine.container.SimpleContainer
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.UpdateLock
import de.hanno.hpengine.engine.transform.calculateMinMax
import kotlinx.coroutines.CoroutineScope
import org.joml.Vector3f
import java.util.logging.Logger

class EntityManager(private val engine: EngineContext<*>, eventBus: EventBus, val scene: Scene) : Manager {
    private val entityContainer: EntityContainer = SimpleContainer()

    var entityMovedInCycle: Long = 0
    var staticEntityMovedInCycle: Long = 0
    var entityAddedInCycle: Long = 0
    var componentAddedInCycle: Long = 0

    var entityHasMoved = false
    var staticEntityHasMoved = false

    init {
        eventBus.register(this)
    }

    fun create(): Entity {
        return Entity()
    }

    fun create(name: String): Entity {
        return create(Vector3f(), name)
    }

    fun create(position: Vector3f, name: String): Entity {

        return Entity(name, position)
    }

    fun add(entity: Entity) {
        entityContainer.add(entity)
        entity.index = entityContainer.entities.indexOf(entity)
    }

    fun add(entities: List<Entity>) {
        for (entity in entities) {
            add(entity)
        }
    }

    fun getEntities() = entityContainer.entities

    override fun clear() {
        entityContainer.clear()
    }

    private fun CoroutineScope.onUpdate(deltaSeconds: Float) {
        entityHasMoved = false
        staticEntityHasMoved = false

        for (i in entityContainer.entities.indices) {
            try {
                with(entityContainer.entities[i]) {
                    update(deltaSeconds)
                }
            } catch (e: Exception) {
                LOGGER.warning(e.message)
            }
        }

        val predicate: (Entity) -> Boolean = {
            it != scene.activeCamera.entity && it.hasComponent(ModelComponent::class.java)
        }
        for (entity in entityContainer.entities.filter(predicate)) {
            if (!entity.hasMoved()) {
                continue
            }

            if (entity.updateType == Update.STATIC) {
                staticEntityHasMoved = true
                staticEntityMovedInCycle = scene.currentCycle
                scene.minMax.calculateMinMax(scene.entityManager.getEntities())
            } else {
                entityHasMoved = true
                entityMovedInCycle = scene.currentCycle
                scene.minMax.calculateMinMax(scene.entityManager.getEntities())
            }
            entity.movedInCycle = scene.currentCycle
            break
        }
    }

    override fun CoroutineScope.afterUpdate(deltaSeconds: Float) {
        onUpdate(deltaSeconds)
        for (entity in entityContainer.entities) {
            entity.isHasMoved = false
        }
    }

    override fun extract(renderState: RenderState) {
        renderState.entitiesState.entityMovedInCycle = entityMovedInCycle
        renderState.entitiesState.staticEntityMovedInCycle = staticEntityMovedInCycle
        renderState.entitiesState.entityAddedInCycle = entityAddedInCycle
        renderState.entitiesState.componentAddedInCycle = componentAddedInCycle
    }

    companion object {
        private val LOGGER = Logger.getLogger(EntityManager::class.java.name)
    }

}
