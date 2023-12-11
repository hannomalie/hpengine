package de.hanno.hpengine.model

import EntityStruktImpl.Companion.sizeInBytes
import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.One
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.artemis.getOrNull
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.instancing.InstanceComponent
import de.hanno.hpengine.instancing.InstancesComponent
import de.hanno.hpengine.model.material.MaterialManager
import de.hanno.hpengine.system.Extractor
import org.koin.core.annotation.Single
import struktgen.api.get

@Single(binds = [BaseSystem::class, Extractor::class, EntityBufferExtractorSystem::class])
@One(
    ModelComponent::class,
)
class EntityBufferExtractorSystem(
    private val materialManager: MaterialManager,
    private val entityBuffer: EntityBuffer,
) : BaseEntitySystem(), Extractor {
    lateinit var modelComponentMapper: ComponentMapper<ModelComponent>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var modelCacheComponentMapper: ComponentMapper<ModelCacheComponent>
    lateinit var instanceComponentMapper: ComponentMapper<InstanceComponent>
    lateinit var materialComponentMapper: ComponentMapper<MaterialComponent>
    lateinit var instancesComponentMapper: ComponentMapper<InstancesComponent>

    override fun processSystem() {
    }

    override fun extract(currentWriteState: RenderState) {
        val entitiesBufferToWrite = currentWriteState[entityBuffer.entitiesBuffer]
        entitiesBufferToWrite.ensureCapacityInBytes(entityBuffer.entityCount * EntityStrukt.sizeInBytes)

        var entityBufferIndex = 0

        forEachEntity { parentEntityId ->
            val modelComponent = modelComponentMapper.getOrNull(parentEntityId)

            val instances = instancesComponentMapper.getOrNull(parentEntityId)?.instances
            val entityIds = if (instances == null) arrayOf(parentEntityId) else arrayOf(parentEntityId) + instances

            for (entityId in entityIds) {
                val instanceComponent = instanceComponentMapper.getOrNull(entityId)
                val materialComponentOrNull = materialComponentMapper.getOrNull(entityId)

                if (modelComponent != null) {
                    val modelCacheComponent =
                        modelCacheComponentMapper.getOrNull(entityId) ?: modelCacheComponentMapper[parentEntityId]
                    val modelIsLoaded = modelCacheComponent != null

                    if (modelIsLoaded) {
                        val model = modelCacheComponent.model
                        when (model) {
                            is AnimatedModel -> model.animations.values.forEach {
                                it.update(world.delta)
                            }

                            is StaticModel -> {}
                        }
                        val transformComponent =
                            transformComponentMapper.getOrNull(entityId) ?: transformComponentMapper.getOrNull(
                                instanceComponent!!.targetEntity
                            )
                        val transform = transformComponent!!.transform

                        val allocation = modelCacheComponent.allocation

                        val meshes = model.meshes


                        val animationFrame = when (model) {
                            is AnimatedModel -> model.animation.currentFrame
                            is StaticModel -> 0
                        }
                        entitiesBufferToWrite.byteBuffer.run {
                            for ((targetMeshIndex, mesh) in meshes.withIndex()) {
                                val currentEntity = entitiesBufferToWrite[entityBufferIndex]

                                val meshMaterial = materialComponentOrNull?.material
                                    ?: mesh.material // TODO: Think about override per mesh instead of all at once
                                val targetMaterialIndex = materialManager.indexOf(meshMaterial)
                                currentEntity.run {
                                    materialIndex = targetMaterialIndex
                                    update = Update.STATIC.value
                                    meshBufferIndex = entityBufferIndex
                                    entityIndex = entityId
                                    meshIndex = targetMeshIndex
                                    baseVertex = allocation.forMeshes[targetMeshIndex].vertexOffset
                                    baseJointIndex = allocation.baseJointIndex
                                    animationFrame0 = animationFrame
                                    isInvertedTexCoordY = if (model.isInvertTexCoordY) 1 else 0
                                    dummy4 = allocation.indexOffset
                                    val boundingVolume =
                                        modelCacheComponent.meshSpatials[targetMeshIndex].boundingVolume

                                    setTrafoAndBoundingVolume(transform.transformation, boundingVolume)
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