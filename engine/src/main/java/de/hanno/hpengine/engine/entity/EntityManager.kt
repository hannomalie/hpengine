package de.hanno.hpengine.engine.entity

import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.container.EntityContainer
import de.hanno.hpengine.engine.container.SimpleContainer
import de.hanno.hpengine.engine.graphics.BatchingSystem
import de.hanno.hpengine.engine.graphics.EntityStruct
import de.hanno.hpengine.engine.graphics.renderer.pipelines.safeCopyTo
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.instancing.instanceCount
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.util.isEqualTo
import de.hanno.struct.StructArray
import de.hanno.struct.enlarge
import org.joml.Matrix4f
import org.joml.Vector3f
import java.util.WeakHashMap
import java.util.logging.Logger

private val movedInCycleExtensionState = ExtensionState<Entity, Long>(0L)
var Entity.movedInCycle by movedInCycleExtensionState

private val indexExtensionState = ExtensionState<Entity, Int>(0)
var Entity.index by indexExtensionState

class EntityManager : Manager {

    private val entityContainer: EntityContainer = SimpleContainer()

    var gpuEntitiesArray = StructArray(size = 1000) { EntityStruct() }
    val entityIndices: MutableMap<ModelComponent, Int> = mutableMapOf()
    val ModelComponent.entityIndex
        get() = entityIndices[this]!!

    private val batchingSystem = BatchingSystem()

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
        val predicate: (Entity) -> Boolean = { !scene.extensions.cameraExtension.run { it.isActiveCameraEntity } }
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
        cacheEntityIndices(scene)
        updateGpuEntitiesArray(scene)
    }

    fun cacheEntityIndices(scene: Scene) {
        entityIndices.clear()
        var index = 0
        for (current in scene.extensions.modelComponentExtension.componentSystem.components) {
            entityIndices[current] = index
            index += current.entity.instanceCount * current.meshes.size
        }
    }

    override fun extract(scene: Scene, renderState: RenderState) {
        gpuEntitiesArray.safeCopyTo(renderState.entitiesBuffer)

        batchingSystem.extract(renderState.camera, renderState, renderState.camera.getPosition(),
            scene.extensions.modelComponentExtension.componentSystem.components, scene.engineContext.config.debug.isDrawLines,
            scene.extensions.modelComponentExtension.componentSystem.allocations, entityIndices)

        renderState.entitiesState.entityMovedInCycle = entityMovedInCycle
        renderState.entitiesState.staticEntityMovedInCycle = staticEntityMovedInCycle
        renderState.entitiesState.entityAddedInCycle = entityAddedInCycle
        renderState.entitiesState.componentAddedInCycle = componentAddedInCycle
    }

    private fun getRequiredEntityBufferSize(scene: Scene): Int {
        return scene.extensions.modelComponentExtension.componentSystem.components.sumBy { it.entity.instanceCount * it.meshes.size }
    }

    private fun updateGpuEntitiesArray(scene: Scene) {
        gpuEntitiesArray = gpuEntitiesArray.enlarge(getRequiredEntityBufferSize(scene))
        gpuEntitiesArray.buffer.rewind()
    }

    companion object {
        private val LOGGER = Logger.getLogger(EntityManager::class.java.name)
    }

}
