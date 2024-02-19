package de.hanno.hpengine.transform

import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.EntitySystem
import com.artemis.annotations.All
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.artemis.getOrNull
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.math.isEqualTo
import de.hanno.hpengine.model.AnimatedModel
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.model.ModelCacheComponent
import de.hanno.hpengine.model.StaticModel
import de.hanno.hpengine.system.Extractor
import org.joml.Matrix4f
import org.koin.core.annotation.Single

@Single(binds = [BaseSystem::class, EntityMovementSystem::class, Extractor::class])
@All(value = [TransformComponent::class])
class EntityMovementSystem(
    private val entitiesStateHolder: EntitiesStateHolder,
): EntitySystem(), Extractor {
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var modelCacheComponentMapper: ComponentMapper<ModelCacheComponent>

    var cycle = 0L

    private val cache = mutableMapOf<Int, MovementInfo>()

    val anyEntityHasMoved get() = anyStaticEntityHasMoved || anyAnimatedEntityHasMoved || nonModelEntityHasMoved

    // This system increments its cycle immediately, so in order to see whether the newest update determined
    // entities as moved, we need to add 1 to the recorded cycles
    val anyStaticEntityHasMoved get() = anyStaticEntityHasMovedInCycle + 1 >= cycle
    val anyAnimatedEntityHasMoved get() = anyAnimatedEntityHasMovedInCycle + 1 >= cycle
    val nonModelEntityHasMoved get() = nonModelEntityHasMovedInCycle +1 >= cycle

    val anyEntityMovedInCycle get() = maxOf(nonModelEntityHasMovedInCycle, maxOf(anyStaticEntityHasMovedInCycle, anyAnimatedEntityHasMovedInCycle))

    var anyStaticEntityHasMovedInCycle = 0L
        private set
    var anyAnimatedEntityHasMovedInCycle = 0L
        private set
    var nonModelEntityHasMovedInCycle = 0L
        private set

    override fun processSystem() {

        forEachEntity { entityId ->
            val currentTransform = transformComponentMapper[entityId].transform.transformation
            val cachedMovementInfo = cache.computeIfAbsent(entityId) {
                MovementInfo(Matrix4f(currentTransform), 0)
            }
            // when someone set -1, it means it has moved right now, in the current cycle
            val wasMovedFromRenderThread = cachedMovementInfo.movedInCycle == -1L

            val modelOrNUll = modelCacheComponentMapper.getOrNull(entityId)?.model
            val hasMoved =  when(modelOrNUll) {
                is AnimatedModel -> {
                    anyAnimatedEntityHasMovedInCycle = cycle
                    true
                }
                is StaticModel -> {
                    val transformHasChanged = !currentTransform.isEqualTo(cachedMovementInfo.transform)
                    val hasMoved = transformHasChanged || wasMovedFromRenderThread
                    if(hasMoved) {
                        anyStaticEntityHasMovedInCycle = cycle
                    }
                    hasMoved
                }
                null -> {
                    val transformHasChanged = !currentTransform.isEqualTo(cachedMovementInfo.transform)
                    val hasMoved = transformHasChanged || wasMovedFromRenderThread
                    if(hasMoved) {
                        nonModelEntityHasMovedInCycle = cycle
                    }
                    hasMoved
                }
            }.apply {
                cachedMovementInfo.transform.set(currentTransform)
            }

            if(hasMoved) {
                cachedMovementInfo.movedInCycle = cycle
            }
        }
        cycle++
    }

    fun entityHasMoved(entityId: Int, currentCycle: Long): Boolean = if(cache.contains(entityId)) {
        cache[entityId]!!.movedInCycle + 1 >= currentCycle
    } else false

    fun setEntityHasMovedInCycle(entityId: Int, cycle: Long) {
        if(cache.contains(entityId)) {
            cache[entityId]!!.movedInCycle = cycle
        }
    }

    fun entityHasMoved(entityId: Int) = entityHasMoved(entityId, cycle)

    override fun extract(currentWriteState: RenderState) {
        val entitiesState = currentWriteState[entitiesStateHolder.entitiesState]
        entitiesState.entityMovedInCycle = anyEntityMovedInCycle
        entitiesState.staticEntityMovedInCycle = anyStaticEntityHasMovedInCycle
    }

    fun cycleEntityHasMovedIn(entityId: Int) = cache[entityId]?.movedInCycle ?: 0
}

class MovementInfo(val transform: Matrix4f, movedInCycle: Long) {
    var movedInCycle: Long = movedInCycle
        set(value) {
            if(value > field || value == -1L) { // when -1 is set, it's by someone who doesn't know the current cpu cycle, like the render thread
                field = value
            }
        }
}
