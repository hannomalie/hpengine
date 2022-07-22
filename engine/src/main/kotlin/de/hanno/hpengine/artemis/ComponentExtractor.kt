package de.hanno.hpengine.artemis
import com.artemis.*
import com.artemis.annotations.All
import com.artemis.annotations.Transient
import com.artemis.annotations.Wire
import com.artemis.utils.Bag
import com.artemis.utils.reflect.ClassReflection.isAnnotationPresent
import com.esotericsoftware.kryo.Kryo
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.system.Extractor
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties

@All
class ComponentExtractor : BaseEntitySystem(), Extractor {
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

    override fun extract(currentWriteState: RenderState) {
        return // This works but is simply too costly
        componentSubclasses.associateWith { clazz ->
            val componentMapper = componentMappers[clazz]!!

            val currentComponents = mapEntity {
                componentMapper.get(it)
            }.filterNotNull()

            val currentExtractedComponents = currentWriteState.componentExtracts[clazz]
            if(currentExtractedComponents == null || currentExtractedComponents.size < currentComponents.size) {
                currentWriteState.componentExtracts[clazz] = componentMapper.hackedOutComponents.map {
                    clazz.cast(clazz.constructors[0].newInstance())
                }
            }

            currentWriteState.componentExtracts[clazz]!!.let { extract ->
                currentComponents.forEachIndexed { index, source ->
                    val target = extract[index]
                    source::class.memberProperties.filterIsInstance<KMutableProperty1<Any, Any>>().forEach { property ->
                        property.set(target, property.get(source))
                    }
                }
            }
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