package de.hanno.hpengine.model

import EntityStruktImpl.Companion.sizeInBytes
import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.One
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.artemis.getOrNull
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.graphics.envprobe.EnvironmentProbesStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.instancing.InstanceComponent
import de.hanno.hpengine.instancing.InstancesComponent
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.toCount
import de.hanno.hpengine.transform.AABB
import org.koin.core.annotation.Single
import struktgen.api.get

@Single(binds = [BaseSystem::class, Extractor::class, EntityBufferExtractorSystem::class])
@One(
    ModelComponent::class,
)
class EntityBufferExtractorSystem(
    private val materialSystem: MaterialSystem,
    private val entityBuffer: EntityBuffer,
    private val environmentProbesStateHolder: EnvironmentProbesStateHolder,
) : BaseEntitySystem(), Extractor {
    lateinit var modelComponentMapper: ComponentMapper<ModelComponent>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var modelCacheComponentMapper: ComponentMapper<ModelCacheComponent>
    lateinit var instanceComponentMapper: ComponentMapper<InstanceComponent>
    lateinit var materialComponentMapper: ComponentMapper<MaterialComponent>
    lateinit var instancesComponentMapper: ComponentMapper<InstancesComponent>

    override fun processSystem() {
    }

    private val tempAABB = AABB()

    override fun extract(currentWriteState: RenderState) {
        val entitiesBufferToWrite = currentWriteState[entityBuffer.entitiesBuffer]
        entitiesBufferToWrite.ensureCapacityInBytes(entityBuffer.entityCount * SizeInBytes(EntityStrukt.sizeInBytes))

        var entityBufferIndex = 0

        forEachEntity { parentEntityId ->
            modelComponentMapper.getOrNull(parentEntityId)?.let { _ ->

                val instances = instancesComponentMapper.getOrNull(parentEntityId)?.instances
                val entityIds = if (instances == null) arrayOf(parentEntityId) else arrayOf(parentEntityId) + instances

                for (entityId in entityIds) {
                    val instanceComponent = instanceComponentMapper.getOrNull(entityId)
                    val materialComponentOrNull = materialComponentMapper.getOrNull(entityId)
                    val modelCacheComponent = modelCacheComponentMapper.getOrNull(entityId, parentEntityId)

                    if (modelCacheComponent != null) {
                        val model = modelCacheComponent.model
                        val transformComponent = transformComponentMapper.getOrNull(entityId) ?: transformComponentMapper.getOrNull(
                            instanceComponent!!.targetEntity
                        )
                        val transform = transformComponent!!.transform

                        val allocation = modelCacheComponent.allocation

                        entitiesBufferToWrite.byteBuffer.run {
                            model.meshes.forEachIndexed { index, mesh ->
                                entitiesBufferToWrite.ensureCapacityInBytes((1+entityBufferIndex).toCount() * SizeInBytes(EntityStrukt.sizeInBytes))
                                val currentEntity = entitiesBufferToWrite[entityBufferIndex]

                                val material = materialComponentOrNull?.material ?: mesh.material // TODO: Think about override per mesh instead of all at once
                                currentEntity.run {
                                    materialIndex = materialSystem.indexOf(material)
                                    update = Update.STATIC.value
                                    meshBufferIndex = entityBufferIndex
                                    entityIndex = entityId
                                    meshIndex = index
                                    baseVertex = allocation.forMeshes[index].vertexOffset.value.toInt()
                                    baseJointIndex = allocation.baseJointIndex
                                    animationFrame0 = model.getAnimationFrame(0)
                                    animationFrame1 = model.getAnimationFrame(1)
                                    animationFrame2 = model.getAnimationFrame(2)
                                    animationFrame3 = model.getAnimationFrame(3)
                                    isInvertedTexCoordY = if (model.isInvertTexCoordY) 1 else 0
                                    probeIndex = 1//environmentProbesStateHolder.getProbeForBatch(currentWriteState, entityBufferIndex) ?: -1
                                    dummy4 = allocation.indexOffset.value.toInt()

                                    setTrafoAndBoundingVolume(transform.transformation, tempAABB.apply {
                                        min.set(mesh.boundingVolume.min)
                                        max.set(mesh.boundingVolume.max)
                                        recalculate(transform)
                                    })
                                }
                                entityBufferIndex++
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Model<*>.getAnimationFrame(index: Int): Int = when (this) {
    is AnimatedModel -> {
        val animations = animationController.animations.values.toList()
        if(index > animations.lastIndex) 0 else animations[index].currentFrame
    }
    is StaticModel -> 0
}
