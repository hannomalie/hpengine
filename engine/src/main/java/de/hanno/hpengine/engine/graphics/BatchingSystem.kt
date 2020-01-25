package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.ModelComponentSystem
import de.hanno.hpengine.engine.model.instanceCount
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
            val distanceToCamera = tempDistVector.length()

            val entityIndexOf = entityIndices[modelComponent]!!

            val meshes = modelComponent.meshes
            for (meshIndex in meshes.indices) {
                val mesh = meshes[meshIndex]
                val meshCenter = mesh.getCenter(entity)
                val boundingSphereRadius = modelComponent.getBoundingSphereRadius(mesh)

                val (min1, max1) = modelComponent.model.getMinMax(mesh)
                val intersectAABB = camera.frustum.frustumIntersection.intersectAab(min1, max1)
                val meshIsInFrustum = intersectAABB == FrustumIntersection.INTERSECT || intersectAABB == FrustumIntersection.INSIDE


                val visibleForCamera = meshIsInFrustum || entity.instanceCount > 1 // TODO: Better culling for instances
                val meshBufferIndex = entityIndexOf + meshIndex * entity.instanceCount

                val batch = (currentWriteState.entitiesState.cash).computeIfAbsent(BatchKey(mesh, -1)) { (_, _) -> RenderBatch() }
                with(batch){ entityBufferIndex = meshBufferIndex
                    this.isDrawLines = drawLines
                    this.cameraWorldPosition = cameraWorldPosition
                    this.isVisibleForCamera = visibleForCamera
                    update = entity.updateType
                    minWorld = min1
                    maxWorld = max1
                    centerWorld = meshCenter
                    this.boundingSphereRadius = boundingSphereRadius
                    with(drawElementsIndirectCommand) {
                        this.primCount = entity.instanceCount
                        this.count = modelComponent.getIndexCount(meshIndex)
                        this.firstIndex = allocations[modelComponent]!!.forMeshes[meshIndex].indexOffset
                        this.baseVertex = allocations[modelComponent]!!.forMeshes[meshIndex].vertexOffset
                    }
                    this.animated = !modelComponent.model.isStatic
                    materialInfo = mesh.material.materialInfo
                    entityIndex = entity.index
                    this.meshIndex = meshIndex
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