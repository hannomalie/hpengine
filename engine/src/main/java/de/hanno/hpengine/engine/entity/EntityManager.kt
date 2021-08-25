package de.hanno.hpengine.engine.entity

import de.hanno.hpengine.engine.container.EntityContainer
import de.hanno.hpengine.engine.container.SimpleContainer
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.extension.CameraExtension
import de.hanno.hpengine.engine.extension.CameraExtension.Companion.cameraEntity
import de.hanno.hpengine.engine.model.EntityBuffer
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.util.isEqualTo
import org.joml.Matrix4f
import org.joml.Vector3f
import org.koin.core.component.get
import java.util.WeakHashMap
import java.util.logging.Logger

private val movedInCycleExtensionState = ExtensionState<Entity, Long>(0L)
var Entity.movedInCycle by movedInCycleExtensionState

private val indexExtensionState = ExtensionState<Entity, Int>(0)
var Entity.index by indexExtensionState

class EntityManager : Manager {

    private val entityContainer: EntityContainer = SimpleContainer()

    var entityMovedInCycle: Long = 0
    var staticEntityMovedInCycle: Long = 0
    var entityAddedInCycle: Long = 0
    var componentAddedInCycle: Long = 0

    private var transformCache = WeakHashMap<Entity, Matrix4f>()

    val movedEntities = WeakHashMap<Entity, Entity>()
    var entityHasMoved = false
    var staticEntityHasMoved = false

    val Entity.hasMoved
        get() = movedEntities.containsKey(this)

    fun create(): Entity = Entity()

    fun create(name: String): Entity = create(Vector3f(), name)

    fun create(position: Vector3f, name: String): Entity = Entity(name, position)

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

    override fun clear() = entityContainer.clear()

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        entityHasMoved = false
        staticEntityHasMoved = false
        movedEntities.clear()
        for (i in entityContainer.entities.indices) {
            try {
                entityContainer.entities[i].update(scene, deltaSeconds)
            } catch (e: Exception) {
                LOGGER.warning(e.message)
            }
        }
        val predicate: (Entity) -> Boolean = { !scene.get<CameraExtension>().run { scene.cameraEntity == it } }
        for (entity in entityContainer.entities.filter(predicate)) {
            transformCache.putIfAbsent(entity, Matrix4f(entity.transform))

            val cachedTransform = transformCache[entity]!!
            val entityMoved = !cachedTransform.isEqualTo(entity.transform)
            if (!entityMoved) {
                continue
            }

            transformCache[entity] = Matrix4f(entity.transform)
            if (entity.updateType == Update.STATIC) {
                staticEntityHasMoved = true
                staticEntityMovedInCycle = scene.currentCycle
            } else {
                entityHasMoved = true
                entityMovedInCycle = scene.currentCycle
            }
            movedEntities[entity] = entity
            scene.calculateBoundingVolume()
            entity.movedInCycle = scene.currentCycle
        }
    }

    override fun extract(scene: Scene, renderState: RenderState) {
        renderState.entitiesState.entityMovedInCycle = entityMovedInCycle
        renderState.entitiesState.staticEntityMovedInCycle = staticEntityMovedInCycle
        renderState.entitiesState.entityAddedInCycle = entityAddedInCycle
        renderState.entitiesState.componentAddedInCycle = componentAddedInCycle
    }

    companion object {
        private val LOGGER = Logger.getLogger(EntityManager::class.java.name)
    }

}
