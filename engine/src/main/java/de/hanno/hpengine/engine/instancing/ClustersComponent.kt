package de.hanno.hpengine.engine.instancing

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.event.EntityAddedEvent
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.model.Cluster
import de.hanno.hpengine.engine.model.Instance
import de.hanno.hpengine.engine.model.loader.md5.AnimationController
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.transform.*
import java.util.*

class ClustersComponent(val engine: Engine, private val eventBus: EventBus, private val entity: Entity): Component {

    private val instances = mutableListOf<Instance>()
    private val clusters = mutableListOf<Cluster>()

    fun getInstances(): List<Instance> = instances
    fun getInstancesMinMaxWorlds(): List<AABB> = instances.map{it -> it.minMaxWorld }

    override fun getEntity() = entity
    override fun getIdentifier(): String = ClustersComponent::class.java.simpleName
    override fun update(seconds: Float) {
        for (cluster in clusters) {
            cluster.update(seconds)
        }
    }

    fun addInstance(transform: Transform<*>) {
        val componentOption = entity.getComponentOption(ModelComponent::class.java, ModelComponent.COMPONENT_KEY)
        val instance: Instance
        if (componentOption.isPresent) {
            val materials = componentOption.get().meshes.map( { it.material })
            val spatial = if (componentOption.get().isStatic) InstanceSpatial() else AnimatedInstanceSpatial()
            val animationController = if (componentOption.get().isStatic) AnimationController(engine, 0, 0f) else AnimationController(engine, 120, 24f)
            instance = Instance(engine, entity, transform, materials, animationController, spatial)
            spatial.instance = instance
            addExistingInstance(instance)
        } else {
            val modelComponent = entity.getComponent(ModelComponent::class.java)
            val materials = if (modelComponent == null) ArrayList<Material>() else modelComponent.materials

            val spatial = object : InstanceSpatial() {
                override fun getMinMax(): AABB {
                    return entity.spatial.minMax
                }
            }
            instance = Instance(engine, entity, transform, materials, AnimationController(engine, 0, 0f), spatial)
            spatial.instance = instance
            addExistingInstance(instance)
        }
        eventBus.post(EntityAddedEvent())
    }

    private fun recalculateInstances() {
        instances.clear()
        for (cluster in clusters) {
            instances.addAll(cluster)
        }
    }

    fun getInstanceCount(): Int {
        val instancesCount = 1 + clusters.sumBy { it.size }

        //        TODO: Check if this makes sense
//        if (entity.hasParent()) {
//            instancesCount *= entity.getParent().getInstanceCount()
//        }
        return instancesCount
    }

    fun addExistingInstance(instance: Instance) {
        val firstCluster = getOrCreateFirstCluster()
        firstCluster.add(instance)
        recalculateInstances()
    }

    fun addInstanceTransforms(instances: List<Transform<out Transform<*>>>) {
        if (entity.parent != null) {
            for (instance in instances) {
                instance.parent = entity.parent
            }
        }
        val firstCluster = getOrCreateFirstCluster()
        val modelComponent = entity.getComponent(ModelComponent::class.java)
        val materials = if (modelComponent == null) ArrayList<Material>() else modelComponent.materials
        val collect = instances.map { trafo ->
            val spatial = InstanceSpatial()
            val instance = Instance(engine, entity, trafo, materials, AnimationController(engine, 0, 0f), spatial)
            spatial.instance = instance
            instance
        }
        firstCluster.addAll(collect)
        recalculateInstances()
        eventBus.post(EntityAddedEvent())
    }

    fun addInstances(instances: List<Instance>) {
        if (entity.parent != null) {
            for (instance in instances) {
                instance.setParent(entity.parent)
            }
        }
        val firstCluster = getOrCreateFirstCluster()
        firstCluster.addAll(instances)
        recalculateInstances()
        eventBus.post(EntityAddedEvent())
    }

    private fun getOrCreateFirstCluster(): Cluster {
        var firstCluster: Cluster? = null
        if (!this.clusters.isEmpty()) {
            firstCluster = this.clusters[0]
        }
        if (firstCluster == null) {
            firstCluster = Cluster()
            clusters.add(firstCluster)
        }
        return firstCluster
    }
    fun getClusters(): List<Cluster> {
        return clusters
    }

    fun addCluster(cluster: Cluster) {
        clusters.add(cluster)
        recalculateInstances()
    }

    companion object {
        val clustersComponentType = ClustersComponent::class.java.simpleName
    }
}

class ClustersComponentSystem(val engine: Engine) : ComponentSystem<ClustersComponent> {
    private val components = mutableListOf<ClustersComponent>()

    override fun getComponents() = components

    override fun update(deltaSeconds: Float) {
        getComponents().forEach{
            it.update(deltaSeconds)
        }
    }

    override fun create(entity: Entity): ClustersComponent {
        return ClustersComponent(engine, engine.eventBus, entity).also { addComponent(it) }
    }

    override fun addComponent(component: ClustersComponent) {
        components.add(component)
    }

    override fun clear() {
    }
}
