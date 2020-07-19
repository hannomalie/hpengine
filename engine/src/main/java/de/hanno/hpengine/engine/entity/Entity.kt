package de.hanno.hpengine.engine.entity

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.PhysicsComponent
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Spatial
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.engine.transform.TransformSpatial
import kotlinx.coroutines.CoroutineScope
import org.joml.Vector3f
import java.util.Optional
import java.util.WeakHashMap
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

// TODO: Use this in slightly changed form to make Entity extendable with attached extension properties.
class SimpleProperty<R, T>(var value: T): ReadWriteProperty<R, T> {
    override fun getValue(thisRef: R, property: KProperty<*>): T = value

    override fun setValue(thisRef: R, property: KProperty<*>, value: T) {
        this.value = value
    }
}

class ExtensionState<R, T>(val defaultValue: T): ReadWriteProperty<R, T> {
    internal val receiverToProperty = WeakHashMap<R, SimpleProperty<*, *>>()

    override fun getValue(thisRef: R, property: KProperty<*>): T {
        val readWriteProperty = receiverToProperty[thisRef] as? SimpleProperty<R, T>
        return readWriteProperty?.getValue(thisRef, property) ?: defaultValue
    }

    override fun setValue(thisRef: R, property: KProperty<*>, value: T) {
        receiverToProperty.putIfAbsent(thisRef, SimpleProperty<R, T>(value))
    }
}

fun <R, T> extensionState(defaultValue: T): ExtensionState<R, T> = ExtensionState(defaultValue)


class Entity @JvmOverloads constructor(var name: String = "Entity" + System.currentTimeMillis().toString(),
                                            position: Vector3f = Vector3f(0f, 0f, 0f)) : Transform<Entity>(), Updatable {
    var movedInCycle = 0L

    private val simpleSpatial = TransformSpatial(this, AABB(Vector3f(-5f), Vector3f(5f)))
    private val spatial: TransformSpatial
        get() = getComponent(ModelComponent::class.java)?.spatial ?: simpleSpatial

    var index = -1

    var updateType = Update.DYNAMIC
        get() = if (hasComponent(PhysicsComponent::class.java) && getComponent(PhysicsComponent::class.java)!!.isDynamic || hasComponent(ModelComponent::class.java) && !getComponent(ModelComponent::class.java)!!.model.isStatic) {
            Update.DYNAMIC
        } else field

        set(value) {
            field = value
            if (hasChildren()) {
                for (child in children) {
                    child.updateType = value
                }
            }
        }

    var isVisible = true

    var components: MutableList<Component> = ArrayList()

    val centerWorld: Vector3f
        get() = spatial.getCenter(this)

    val minMaxWorld: AABB
        get() = spatial.getMinMax(this)

    val minMax: AABB
        get() = spatial.minMax

    val boundingSphereRadius: Float
        get() = spatial.getBoundingSphereRadius(this)

    init {
        setTranslation(position)
    }

    fun addComponent(component: Component) {
        if(components.contains(component)) { return }

        components.add(component)
    }

    fun <T : Component> getComponent(type: Class<T>): T? {
        var i = 0
        val size = components.size
        while(i < size) {
            val component = components[i]
            if(type.isAssignableFrom(component.javaClass)) return type.cast(component)
            i++
        }
        return null
//        Worse allocation performance than the above
//        return components
//                .filter { type.isAssignableFrom(it.javaClass) }
//                .map { type.cast(it) }
//                .firstOrNull()
    }

    fun <T : Component> getComponents(type: Class<T>): List<T> = components
        .filter { type.isAssignableFrom(it.javaClass) }
        .map { type.cast(it) }

    fun <T : Component> getComponentOption(type: Class<T>) = Optional.ofNullable(getComponent(type))

    fun hasComponent(type: Class<out Component>): Boolean = getComponent(type) != null
    fun hasComponents(types: List<Class<out Component>>) = types.all { type -> hasComponent(type) }
    fun getComponents(types: List<Class<out Component>>) = types.flatMap { type -> getComponents(type) }

    override fun setParent(node: Entity) {
        super.setParent(node)
        recalculate()
    }

    override fun CoroutineScope.update(deltaSeconds: Float) {
        if (hasParent()) {
            return
        }
        recalculateIfDirty()
        for (i in 0 until children.size) {
            with(children[i] as Entity) {
                update(deltaSeconds)
            }
        }
    }

    fun isInFrustum(camera: Camera): Boolean {
        return Spatial.isInFrustum(camera, spatial.getCenter(this), spatial.getMinMax(this).min, spatial.getMinMax(this).max)
    }

    override fun toString(): String {
        return name
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Entity) {
            return false
        }

        val b = other as Entity?

        return b!!.name == name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun setHasMoved(value: Boolean) {
        super.setHasMoved(value)
        val modelComponentOption = getComponentOption(ModelComponent::class.java)
        modelComponentOption.ifPresent { modelComponent -> modelComponent.isHasUpdated = value }

        val clusters = getComponent(ClustersComponent::class.java)
        if (clusters != null) {
            for (cluster in clusters.getClusters()) {
                cluster.isHasMoved = value
            }
        }
    }

    fun hasMoved(): Boolean {
        val modelComponentOrNull = getComponent(ModelComponent::class.java)
        if (modelComponentOrNull != null) {
            if (modelComponentOrNull.isHasUpdated) {
                return true
            }
        }

        if (isHasMoved) {
            return true
        }
        if (getComponent(ClustersComponent::class.java) == null) {
            return false
        }

        val clusters = getComponent(ClustersComponent::class.java)
        if (clusters != null) {
            for (element in clusters.getClusters()) {
                if (element.isHasMoved) {
                    return true
                }
            }
        }
        return false
    }

}
