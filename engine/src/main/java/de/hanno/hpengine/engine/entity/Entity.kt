package de.hanno.hpengine.engine.entity

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Spatial
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.engine.transform.TransformSpatial
import de.hanno.hpengine.util.Parentable
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

private var entityCounter = 0
class Entity @JvmOverloads constructor(var name: String = "Entity" + entityCounter++,
                                            position: Vector3f = Vector3f(0f, 0f, 0f)): Parentable<Entity>, Updatable {
    val transform: Transform = Transform()

    private val simpleSpatial = TransformSpatial(transform, AABB(Vector3f(-5f), Vector3f(5f)))
    var spatial: TransformSpatial = simpleSpatial

    var updateType = Update.STATIC
        set(value) {
            field = value
            if (hasChildren()) {
                for (child in children) {
                    child.updateType = value
                }
            }
        }

    override fun addChild(child: Entity) {
        transform.addChild(child.transform)
        if(!hasChildInHierarchy(child)) {
            children.add(child)
        }
    }

    override fun removeChild(child: Entity) {
        transform.removeChild(child.transform)
        children.remove(child)
    }

    override val children: MutableList<Entity> = ArrayList()
    override var parent: Entity? = null
        set(value) {
            transform.parent = value?.parent?.transform
            field = value
        }

    var visible = true
    var components: MutableList<Component> = ArrayList()

    val centerWorld: Vector3f
        get() = spatial.getCenter(transform)

    val boundingVolume: AABB
        get() = spatial.getBoundingVolume(transform)

    val boundingSphereRadius: Float
        get() = spatial.getBoundingSphereRadius(transform)

    init {
        transform.setTranslation(position)
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

    override fun CoroutineScope.update(scene: Scene, deltaSeconds: Float) {
        if (hasParent) {
            return
        }
        for (i in 0 until children.size) {
            with(children[i]) {
                update(scene, deltaSeconds)
            }
        }
    }

    override fun toString(): String = name

    override fun equals(other: Any?): Boolean {
        if (other !is Entity) {
            return false
        }

        val b = other as Entity?

        return b!!.name == name
    }

    override fun hashCode(): Int = name.hashCode()

}

fun Entity.isInFrustum(camera: Camera): Boolean {
    return Spatial.isInFrustum(camera, spatial.getCenter(transform), spatial.getBoundingVolume(transform).min, spatial.getBoundingVolume(transform).max)
}
