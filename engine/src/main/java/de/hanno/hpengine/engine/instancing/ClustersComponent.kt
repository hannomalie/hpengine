package de.hanno.hpengine.engine.instancing

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.event.EntityAddedEvent
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.model.Cluster
import de.hanno.hpengine.engine.model.Instance
import de.hanno.hpengine.engine.model.loader.md5.AnimationController
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.AnimatedTransformSpatial
import de.hanno.hpengine.engine.transform.Spatial
import de.hanno.hpengine.engine.transform.StaticTransformSpatial
import de.hanno.hpengine.engine.transform.Transform
import java.util.concurrent.CopyOnWriteArrayList

class ClustersComponent(val engine: EngineContext<*>, private val eventBus: EventBus, private val entity: Entity): Component {

    private val instances = CopyOnWriteArrayList<Instance>()
    private val clusters = CopyOnWriteArrayList<Cluster>()

    fun getInstances(): List<Instance> = instances
    fun getInstancesMinMaxWorlds(): List<AABB> = instances.map{it -> it.minMaxWorld }

    override fun getEntity() = entity
    override fun getIdentifier(): String = ClustersComponent::class.java.simpleName
    override fun update(seconds: Float) {
        for (cluster in clusters) {
            cluster.update(seconds)
        }
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

    fun getOrCreateFirstCluster(): Cluster {
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
    fun addClusters(clusters: List<Cluster>) {
        this.clusters.addAll(clusters)
        recalculateInstances()
    }

    companion object {
        val clustersComponentType = ClustersComponent::class.java.simpleName


        @JvmStatic fun addInstance(entity: Entity, clustersComponent: ClustersComponent, transform: Transform<*>, spatial: Spatial) {
            addInstance(entity, clustersComponent.getOrCreateFirstCluster(), transform, spatial)
        }
        @JvmStatic fun addInstance(entity: Entity, cluster: Cluster, transform: Transform<*>, spatial: Spatial) {
            cluster.add(Instance(entity, transform, animationController = AnimationController(0, 0f), spatial = spatial))
//            eventBus.post(EntityAddedEvent()) TODO: Move this to call site
        }
        @JvmStatic fun addInstance(entity: Entity,
                                   cluster: Cluster,
                                   transform: Transform<*>,
                                   modelComponent: ModelComponent,
                                   materials: List<SimpleMaterial> = modelComponent.materials,
                                   animationController: AnimationController = if (modelComponent.isStatic) AnimationController(0, 0f) else AnimationController(120, 24f),
                                   spatial: Spatial = if (modelComponent.isStatic) AnimatedTransformSpatial(transform, modelComponent) else StaticTransformSpatial(transform, modelComponent)) {

            val instance = Instance(entity, transform, materials, animationController, spatial)
            cluster.add(instance)
//            eventBus.post(EntityAddedEvent()) TODO: Move this to call site
        }
    }

}

class ClustersComponentSystem(val engine: EngineContext<*>) : ComponentSystem<ClustersComponent> {
    override val componentClass: Class<ClustersComponent> = ClustersComponent::class.java
    private val components = mutableListOf<ClustersComponent>()
    val instances = mutableListOf<Instance>()
    val entityInstances = mutableMapOf<Entity, MutableList<Instance>>()


    override fun getComponents() = components

    override fun update(deltaSeconds: Float) {
        components.forEach{
            it.update(deltaSeconds)
        }
    }

    override fun create(entity: Entity): ClustersComponent {
        return ClustersComponent(engine, engine.eventBus, entity)
    }

    override fun addComponent(component: ClustersComponent) {
        components.add(component)
        instances.addAll(component.getInstances())
        entityInstances[component.entity] = ArrayList(component.getInstances())
    }

    override fun clear() {
    }
}
