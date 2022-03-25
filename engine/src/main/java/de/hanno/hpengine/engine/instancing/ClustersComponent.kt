package de.hanno.hpengine.engine.instancing

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.model.AnimatedModel
import de.hanno.hpengine.engine.model.Cluster
import de.hanno.hpengine.engine.model.Instance
import de.hanno.hpengine.engine.model.animation.AnimationController
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.AnimatedTransformSpatial
import de.hanno.hpengine.engine.transform.StaticTransformSpatial
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.engine.transform.TransformSpatial
import java.lang.IllegalStateException
import java.util.concurrent.CopyOnWriteArrayList

class ClustersComponent(override val entity: Entity): Component {

    private val instances = CopyOnWriteArrayList<Instance>()
    private val clusters = CopyOnWriteArrayList<Cluster>()

    fun getInstances(): List<Instance> = instances
    fun getInstancesBoundingVolumes(): List<AABB> = instances.map { it.boundingVolume }

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        this@ClustersComponent.clusters.map { cluster ->
            cluster.update(scene, deltaSeconds)
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
        val parent = entity.parent
        if (parent != null) {
            for (instance in instances) {
                // TODO: This can never succeed
                throw IllegalStateException("Fix parenting stuff")
                instance.parent = (parent as Instance)
            }
        }
        val firstCluster = getOrCreateFirstCluster()
        firstCluster.addAll(instances)
        recalculateInstances()
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


        @JvmStatic fun addInstance(entity: Entity, clustersComponent: ClustersComponent, transform: Transform, spatial: TransformSpatial) {
            addInstance(entity, clustersComponent.getOrCreateFirstCluster(), transform, spatial)
        }
        @JvmStatic fun addInstance(entity: Entity, cluster: Cluster, transform: Transform, spatial: TransformSpatial) {
            cluster.add(Instance(entity, transform, animationController = null, spatial = spatial))
//            eventBus.post(EntityAddedEvent()) TODO: Move this to call site
        }
        @JvmStatic fun addInstance(entity: Entity,
                                   cluster: Cluster,
                                   transform: Transform,
                                   modelComponent: ModelComponent,
                                   materials: List<Material> = modelComponent.materials,
                                   animationController: AnimationController? = if (modelComponent.isStatic) null else AnimationController((modelComponent.model as AnimatedModel).animation),
                                   spatial: TransformSpatial = if (modelComponent.isStatic) AnimatedTransformSpatial(transform, modelComponent) else StaticTransformSpatial(transform, modelComponent)) {

            val instance = Instance(entity, transform, materials, animationController, spatial)
            cluster.add(instance)
//            eventBus.post(EntityAddedEvent()) TODO: Move this to call site
        }
    }

}

val Entity.instances: List<Instance>
    get() = this.getComponent(ClustersComponent::class.java)?.getInstances() ?: emptyList()

val Entity.clusters: List<Cluster>
    get() = this.getComponent(ClustersComponent::class.java)?.getClusters() ?: emptyList()

val Entity.instanceCount: Int
    get() = this.getComponent(ClustersComponent::class.java)?.getInstanceCount() ?: 1