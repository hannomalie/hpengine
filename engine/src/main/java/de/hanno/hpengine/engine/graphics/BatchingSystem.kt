package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.index
import de.hanno.hpengine.engine.entity.movedInCycle
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.instancing.clusters
import de.hanno.hpengine.engine.instancing.instanceCount
import de.hanno.hpengine.engine.model.ModelComponentSystem
import de.hanno.hpengine.engine.scene.BatchKey
import org.joml.FrustumIntersection
import org.joml.Vector3f

class BatchingSystem {

    private val tempDistVector = Vector3f()

    fun extract(camera: Camera, currentWriteState: RenderState, cameraWorldPosition: Vector3f,
                modelComponents: List<ModelComponent>, drawLines: Boolean,
                allocations: MutableMap<ModelComponent, ModelComponentSystem.Allocation>,
                entityIndices: MutableMap<ModelComponent, Int>) {

        currentWriteState.entitiesState.renderBatchesStatic.clear()
        currentWriteState.entitiesState.renderBatchesAnimated.clear()

        for (modelComponent in modelComponents) {
            val entity = modelComponent.entity

            val entityIndexOf = entityIndices[modelComponent]!!

            val meshes = modelComponent.meshes
            for (meshIndex in meshes.indices) {
                val mesh = meshes[meshIndex]
                val meshCenter = mesh.spatial.getCenter(entity.transform)
                val boundingSphereRadius = modelComponent.getBoundingSphereRadius(mesh)

                val (min1, max1) = modelComponent.model.getBoundingVolume(entity.transform, mesh)
                val intersectAABB = camera.frustum.frustumIntersection.intersectAab(min1, max1)
                val meshIsInFrustum = intersectAABB == FrustumIntersection.INTERSECT || intersectAABB == FrustumIntersection.INSIDE


                val visibleForCamera = meshIsInFrustum || entity.instanceCount > 1 // TODO: Better culling for instances
                val meshBufferIndex = entityIndexOf + meshIndex * entity.instanceCount

                val batch = (currentWriteState.entitiesState.cash).computeIfAbsent(BatchKey(mesh, -1)) { (_, _) -> RenderBatch() }
                with(batch){
                    entityBufferIndex = meshBufferIndex
                    this.movedInCycle = entity.movedInCycle
                    this.isDrawLines = drawLines
                    this.cameraWorldPosition = cameraWorldPosition
                    this.isVisibleForCamera = visibleForCamera
                    update = entity.updateType
                    entityMinWorld.set(entity.boundingVolume.min)
                    entityMaxWorld.set(entity.boundingVolume.max)
                    meshMinWorld.set(min1)
                    meshMaxWorld.set(max1)
                    centerWorld = meshCenter
                    this.boundingSphereRadius = boundingSphereRadius
                    with(drawElementsIndirectCommand) {
                        this.primCount = 1
                        this.count = modelComponent.getIndexCount(meshIndex)
                        this.firstIndex = allocations[modelComponent]!!.forMeshes[meshIndex].indexOffset
                        this.baseVertex = allocations[modelComponent]!!.forMeshes[meshIndex].vertexOffset
                    }
                    this.animated = !modelComponent.model.isStatic
                    materialInfo = mesh.material.materialInfo
                    entityIndex = entity.index
                    entityName = entity.name
                    this.meshIndex = meshIndex
                    this.program = modelComponent.program
                }

                if (batch.isStatic) {
                    currentWriteState.addStatic(batch)
                } else {
                    currentWriteState.addAnimated(batch)
                }

                modelComponent.entity.clusters.forEachIndexed { index, cluster ->
                    val instanceCountToDraw = cluster.instanceCountToDraw(camera)
                    val clusterIsCulled = instanceCountToDraw == 0
                    if(!clusterIsCulled) {
                        val batch = (currentWriteState.entitiesState.cash).computeIfAbsent(BatchKey(mesh, index)) { (_, _) -> RenderBatch() }
                        with(batch){
                            entityBufferIndex = meshBufferIndex + modelComponent.entity.clusters.subList(0, index).sumBy { it.size }
                            this.movedInCycle = entity.movedInCycle
                            this.isDrawLines = drawLines
                            this.cameraWorldPosition = cameraWorldPosition
                            this.isVisibleForCamera = visibleForCamera
                            update = entity.updateType
                            entityMinWorld.set(cluster.boundingVolume.min)
                            entityMaxWorld.set(cluster.boundingVolume.max)
                            meshMinWorld.set(cluster.boundingVolume.min)
                            meshMaxWorld.set(cluster.boundingVolume.max)
                            centerWorld = cluster.boundingVolume.center
                            this.boundingSphereRadius = cluster.boundingVolume.boundingSphereRadius
                            with(drawElementsIndirectCommand) {
                                this.primCount = instanceCountToDraw//cluster.size
                                this.count = modelComponent.getIndexCount(meshIndex)
                                this.firstIndex = allocations[modelComponent]!!.forMeshes[meshIndex].indexOffset
                                this.baseVertex = allocations[modelComponent]!!.forMeshes[meshIndex].vertexOffset
                            }
                            this.animated = !modelComponent.model.isStatic
                            materialInfo = mesh.material.materialInfo
                            entityIndex = entity.index
                            entityName = entity.name
                            this.meshIndex = meshIndex
                            this.program = modelComponent.program
                        }

                        if (batch.isStatic) {
                            currentWriteState.addStatic(batch)
                        } else {
                            currentWriteState.addAnimated(batch)
                        }
                    }
                }
            }
        }
    }

}
