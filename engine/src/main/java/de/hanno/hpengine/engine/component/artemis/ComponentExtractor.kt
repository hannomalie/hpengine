package de.hanno.hpengine.engine.component.artemis
import com.artemis.*
import com.artemis.annotations.All
import com.artemis.annotations.Transient
import com.artemis.annotations.Wire
import com.artemis.utils.Bag
import com.artemis.utils.reflect.ClassReflection.isAnnotationPresent
import com.esotericsoftware.kryo.Kryo
import de.hanno.hpengine.engine.graphics.state.RenderState

@All
class ComponentExtractor : BaseEntitySystem() {
    private lateinit var componentSubclasses: List<Class<out Component>>
    private lateinit var componentMappers: Map<Class<out Component>, ComponentMapper<out Component>>

    private lateinit var componentManager: ComponentManager

    @Wire
    lateinit var kryo: Kryo

    public override fun initialize() {
        componentSubclasses = componentManager.componentTypes.map { it.type }.filterNot { isAnnotationPresent(it, Transient::class.java) }
        componentMappers = componentSubclasses.associateWith {
            world.getMapper(it)
        }
    }

    fun extract(currentWriteState: RenderState) {
        currentWriteState.componentExtracts = componentSubclasses.associateWith { clazz ->
            val componentMapper = componentMappers[clazz]!!
            kryo.copy(componentMapper.hackedOutComponents)
        }

        currentWriteState.componentsForEntities.clear()
        val extractedIds = mutableListOf<Int>()
        val actives = subscription.entities
        val ids = actives.data
        var i = 0
        val s = actives.size()
        while (s > i) {
            val entityId = ids[i]
            extractedIds.add(entityId)
            currentWriteState.componentsForEntities[entityId] = componentManager.getComponentsFor(entityId, Bag())
            i++
        }
        currentWriteState.entityIds = extractedIds
    }

    override fun processSystem() {
        // TODO: This might not be sufficient
        if(componentManager.componentTypes.size() != componentSubclasses.size) {
            initialize()
        }
    }
}