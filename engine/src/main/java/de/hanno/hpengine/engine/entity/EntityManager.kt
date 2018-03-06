package de.hanno.hpengine.engine.entity

import de.hanno.hpengine.engine.Engine

import de.hanno.hpengine.engine.container.EntityContainer
import de.hanno.hpengine.engine.container.SimpleContainer
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.model.Update
import org.joml.Vector3f
import java.util.logging.Logger
class EntityManager(private val engine: Engine, eventBus: EventBus) : Manager {
    private val entityContainer: EntityContainer = SimpleContainer()
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

    override fun update(deltaSeconds: Float) {

        entityHasMoved = false
        staticEntityHasMoved = false

        for (i in entityContainer.entities.indices) {
            try {
                entityContainer.entities[i].update(deltaSeconds)
            } catch (e: Exception) {
                LOGGER.warning(e.message)
            }

        }

        val currentScene = engine.getScene()

        for (entity in entityContainer.entities.filter { it != currentScene.activeCamera }) {
            if (!entity.hasMoved()) {
                continue
            }
            currentScene.calculateMinMax()
            entity.isHasMoved = false
            currentScene.setEntityMovedInCycleToCurrentCycle()
            entityHasMoved = true
            if(entity.update == Update.STATIC) {
                staticEntityHasMoved = true
            }
            break
        }
    }
    companion object {
        private val LOGGER = Logger.getLogger(EntityManager::class.java.name)
    }
}
