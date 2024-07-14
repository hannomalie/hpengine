package de.hanno.hpengine.model

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.One
import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.artemis.getOrNull
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.renderer.RenderBatches
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.instancing.InstanceComponent
import de.hanno.hpengine.instancing.InstancesComponent
import de.hanno.hpengine.scene.VertexIndexOffsets
import de.hanno.hpengine.scene.VertexOffsets
import de.hanno.hpengine.scene.dsl.AnimatedModelComponentDescription
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.system.PrioritySystem
import de.hanno.hpengine.toCount
import de.hanno.hpengine.transform.AABB
import de.hanno.hpengine.visibility.InvisibleComponent
import org.apache.logging.log4j.LogManager
import org.joml.FrustumIntersection
import org.koin.core.annotation.Single

@Single(binds = [BaseSystem::class, Extractor::class, DefaultBatchesSystem::class])
@One(ModelComponent::class,)
class DefaultBatchesSystem(
    private val config: Config,
    private val entityBuffer: EntityBuffer,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val modelSystem: ModelSystem,
    private val renderStateContext: RenderStateContext,
) : BaseEntitySystem(), Extractor, PrioritySystem {
    private val logger = LogManager.getLogger(DefaultBatchesSystem::class.java)

    override val priority = 10000

    lateinit var modelComponentMapper: ComponentMapper<ModelComponent>
    lateinit var preventDefaultRenderingComponentMapper: ComponentMapper<PreventDefaultRendering>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var modelCacheComponentMapper: ComponentMapper<ModelCacheComponent>
    lateinit var invisibleComponentMapper: ComponentMapper<InvisibleComponent>
    lateinit var instanceComponentMapper: ComponentMapper<InstanceComponent>
    lateinit var materialComponentMapper: ComponentMapper<MaterialComponent>
    lateinit var instancesComponentMapper: ComponentMapper<InstancesComponent>

    val renderBatchesStatic = renderStateContext.renderState.registerState { RenderBatches() }
    val renderBatchesAnimated = renderStateContext.renderState.registerState { RenderBatches() }

    override fun processSystem() { }

    override fun extract(currentWriteState: RenderState) {
        val camera = currentWriteState[primaryCameraStateHolder.camera]

        currentWriteState[renderBatchesStatic].clear()
        currentWriteState[renderBatchesAnimated].clear()

        forEachEntity { parentEntityId ->
            logger.trace("Processing $parentEntityId")
            val instances = instancesComponentMapper.getOrNull(parentEntityId)?.instances ?: emptyList()
            val entityIds = listOf(parentEntityId) + instances
            val instanceCount = entityIds.size.toCount()

            val modelComponent = modelComponentMapper.getOrNull(parentEntityId)
            val modelCacheComponent = modelCacheComponentMapper.getOrNull(parentEntityId)

            if (preventDefaultRenderingComponentMapper[parentEntityId] == null && modelComponent != null && modelCacheComponent != null) {

                val instanceComponent = instanceComponentMapper.getOrNull(parentEntityId)
                val transformComponent = transformComponentMapper.getOrNull(parentEntityId) ?: transformComponentMapper.get(
                    instanceComponent!!.targetEntity
                )
                val transform = transformComponent.transform

                val materialComponentOrNull = materialComponentMapper.getOrNull(parentEntityId) ?: instanceComponent?.targetEntity?.let { targetEntity ->
                    materialComponentMapper.getOrNull(targetEntity)
                }
                val entityVisible = !invisibleComponentMapper.has(parentEntityId)

                val entityIndexOf = entityBuffer.getEntityIndex(parentEntityId)!! // TODO: Factor in instance index here

                val model: Model<*> = modelCacheComponent.model
                val meshes = model.meshes
                val aabb = AABB()
                for (meshIndex in meshes.indices) {
                    val mesh = meshes[meshIndex]
                    aabb.apply {
                        min.set(mesh.boundingVolume.min)
                        max.set(mesh.boundingVolume.max)
                        recalculate(transform)
                    }

                    val visibleForCamera = camera.contains(aabb) || instanceCount > 1.toCount() // TODO: Better culling for instances
                    val meshBufferIndex = entityIndexOf + meshIndex //* entity.instanceCount

                    val allocation = modelSystem.allocations[modelComponent.modelComponentDescription]!!.forMeshes[meshIndex]
                    val meshMaterial = materialComponentOrNull?.material ?: mesh.material // TODO: Think about override per mesh instead of all at once

                    val batch = getOrCreateBatch(currentWriteState, mesh, entityIndexOf)

                    batch.entityId = parentEntityId
                    batch.entityBufferIndex = meshBufferIndex
                    batch.movedInCycle = currentWriteState.cycle// entity.movedInCycle TODO: reimplement
                    batch.isDrawLines = config.debug.isDrawLines
                    batch.cameraWorldPosition = camera.getPosition()
                    batch.isVisible = entityVisible
                    batch.isVisibleForCamera = visibleForCamera
                    batch.update = when(modelComponent.modelComponentDescription) {
                        is AnimatedModelComponentDescription -> Update.DYNAMIC
                        is StaticModelComponentDescription -> Update.STATIC
                    }
                    batch.entityMinWorld.set(aabb.min)
                    batch.entityMaxWorld.set(aabb.max)
                    batch.meshMinWorld.set(aabb.min)
                    batch.meshMaxWorld.set(aabb.max)
                    batch.centerWorld.set(aabb.center)
                    batch.boundingSphereRadius = aabb.boundingSphereRadius
                    batch.drawElementsIndirectCommand.instanceCount = instanceCount
                    batch.drawElementsIndirectCommand.count = model.meshIndexCounts[meshIndex]
                    batch.drawElementsIndirectCommand.firstIndex = when(allocation) {
                        is VertexIndexOffsets -> allocation.indexOffset
                        is VertexOffsets -> ElementCount(0)
                    }
                    batch.drawElementsIndirectCommand.baseVertex = allocation.vertexOffset
                    batch.animated = !model.isStatic
                    batch.material = meshMaterial
                    batch.program = meshMaterial.programDescription?.let { modelSystem.programCache[it]!! }
                    batch.entityIndex = entityIndexOf
                    //TODO: check if correct index, it got out of hand
                    batch.entityName = mesh.name // TODO: use entity name component
                    batch.contributesToGi = true//entity.contributesToGi TODO: reimplement
                    batch.meshIndex = meshIndex

                    val targetBatches = if (batch.isStatic) {
                        currentWriteState[renderBatchesStatic]
                    } else {
                        currentWriteState[renderBatchesAnimated]
                    }
                    targetBatches.add(batch)
                }
            }
        }
        logger.trace("Currently ${currentWriteState[renderBatchesStatic].size} static batches")
        logger.trace("Currently ${currentWriteState[renderBatchesAnimated].size} animated batches")
    }

    private fun Camera.contains(aabb: AABB): Boolean {
        val intersectAABB = frustum.frustumIntersection.intersectAab(aabb.min, aabb.max)
        return intersectAABB == FrustumIntersection.INTERSECT || intersectAABB == FrustumIntersection.INSIDE
    }

    private fun getOrCreateBatch(
        currentWriteState: RenderState,
        mesh: Mesh<out Any?>,
        entityIndexOf: Int,
    ): RenderBatch {
        val batchKey = BatchKey(mesh, entityIndexOf, -1)
        return currentWriteState[entitiesStateHolder.entitiesState].cash.computeIfAbsent(batchKey) { RenderBatch() }
    }
}