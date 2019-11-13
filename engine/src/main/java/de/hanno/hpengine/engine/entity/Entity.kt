package de.hanno.hpengine.engine.entity

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.PhysicsComponent
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.manager.ManagerRegistry
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.SimpleSpatial
import de.hanno.hpengine.engine.transform.Spatial
import de.hanno.hpengine.engine.transform.Transform
import kotlinx.coroutines.CoroutineScope
import org.joml.Vector3f
import java.util.ArrayList
import java.util.HashMap
import java.util.Optional
import java.util.concurrent.Callable
import java.util.function.Supplier

open class Entity @JvmOverloads constructor(name: String = "Entity" + System.currentTimeMillis().toString(),
                                            position: Vector3f = Vector3f(0f, 0f, 0f)) : Transform<Entity>(), Updatable {
    internal var managerRegistry: ManagerRegistry? = null

    val spatial: SimpleSpatial = object : SimpleSpatial() {
        override val minMax: AABB
            get() = if (hasComponent(ModelComponent::class.java)) {
                val modelComponent = getComponent(ModelComponent::class.java)
                modelComponent!!.minMax
            } else {
                super.minMax
            }
    }

    var index = -1

    var updateType = Update.DYNAMIC
        get() {
            return if (hasComponent(PhysicsComponent::class.java) && getComponent(PhysicsComponent::class.java)!!.isDynamic || hasComponent(ModelComponent::class.java) && !getComponent(ModelComponent::class.java)!!.model.isStatic) {
                Update.DYNAMIC
            } else field
        }
        set(value) {
            field = value
            if (hasChildren()) {
                for (child in children) {
                    child.updateType = value
                }
            }
        }

    open var name = "Entity_" + System.currentTimeMillis()

    open var isSelected = false
    var isVisible = true

    var components: MutableMap<Class<out Component>, Component> = HashMap()

    val allChildrenAndSelf: List<Entity>
        get() {
            val allChildrenAndSelf = ArrayList<Entity>()
            allChildrenAndSelf.add(this)
            if (hasChildren()) {
                for (child in children) {
                    allChildrenAndSelf.addAll(child.allChildrenAndSelf)
                }
            }
            return allChildrenAndSelf
        }

    val centerWorld: Vector3f
        get() = spatial.getCenterWorld(this)

    open val minMaxWorld: AABB
        get() = spatial.getMinMaxWorld(this)
    val minMax: AABB
        get() = spatial.minMax

    val boundingSphereRadius: Float
        get() = spatial.getBoundingSphereRadius(this)

    private val emptyList = ArrayList<AABB>()
    val instanceMinMaxWorlds: List<AABB>
        get() {
            val clusters = getComponent(ClustersComponent::class.java) ?: return emptyList
            return clusters.getInstancesMinMaxWorlds()
        }

    init {
        this.name = name
        setTranslation(position)
    }

    fun addComponent(component: Component): Entity {
        var clazz = component.javaClass
        val isAnonymous = clazz.enclosingClass != null
        if (isAnonymous) {
            clazz = clazz.superclass as Class<Component>
        }
        addComponent(component, clazz)
        return this
    }

    fun addComponent(component: Component, clazz: Class<Component>) {
        components[clazz] = component
        managerRegistry?.onComponentAdded(component)
    }

    fun removeComponent(component: Component) {
        (components as java.util.Map<Class<Component>, Component>).remove(component.identifier, component)
    }

    fun <T : Component> getOrAddComponent(type: Class<T>, supplier: Supplier<T>): T? {
        if (!hasComponent(type)) {
            val component = supplier.get()
            addComponent(component)
            return component
        }
        return getComponent(type)
    }

    fun <T : Component> getOrAddComponentLegacy(type: Class<T>, supplier: Callable<T>): T? {
        if (!hasComponent(type)) {
            var component: T? = null
            try {
                component = supplier.call()
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }

            addComponent(component!!)
            return component
        }
        return getComponent(type)
    }

    fun <T : Component> getComponent(type: Class<T>): T? {
        val component = components[type]
        return type.cast(component)
    }

    fun <T : Component> getComponentOption(type: Class<T>): Optional<T> {
        val component = components[type]
        return Optional.ofNullable(type.cast(component))
    }

    fun hasComponent(type: Class<out Component>): Boolean {
        return components.containsKey(type)
    }

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

    open fun isInFrustum(camera: Camera): Boolean {
        return Spatial.isInFrustum(camera, spatial.getCenterWorld(this), spatial.getMinMaxWorld(this).min, spatial.getMinMaxWorld(this).max)
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
        val modelComponentOption = getComponentOption(ModelComponent::class.java)
        if (modelComponentOption.isPresent) {
            if (modelComponentOption.get().isHasUpdated) {
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
            for (i in 0 until clusters.getClusters().size) {
                if (clusters.getClusters()[i].isHasMoved) {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        private val serialVersionUID: Long = 1
    }

}
