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

        for (modelComponent in modelComponents) {
            val entity = modelComponent.entity
            val distanceToCamera = tempDistVector.length()
            val isInReachForTextureLoading = distanceToCamera < 50 || distanceToCamera < 2.5f * modelComponent.boundingSphereRadius

            val entityIndexOf = entityIndices[modelComponent]!!

            val meshes = modelComponent.meshes
            for (meshIndex in meshes.indices) {
                val mesh = meshes[meshIndex]
                val meshCenter = mesh.getCenter(entity)
                val boundingSphereRadius = modelComponent.getBoundingSphereRadius(mesh)

                val (min1, max1) = modelComponent.getMinMax(entity, mesh)
                val intersectAABB = camera.frustum.frustumIntersection.intersectAab(min1, max1)
                val meshIsInFrustum = intersectAABB == FrustumIntersection.INTERSECT || intersectAABB == FrustumIntersection.INSIDE


                val visibleForCamera = meshIsInFrustum || entity.instanceCount > 1 // TODO: Better culling for instances
                val meshBufferIndex = entityIndexOf + meshIndex * entity.instanceCount

                val batch = (currentWriteState.entitiesState.cash).computeIfAbsent(BatchKey(mesh, -1)) { (_, _) -> RenderBatch() }
                batch.init(meshBufferIndex, entity.isVisible, entity.isSelected, drawLines, cameraWorldPosition,
                        isInReachForTextureLoading, entity.instanceCount, visibleForCamera, entity.updateType,
                        min1, max1, meshCenter, boundingSphereRadius, modelComponent.getIndexCount(meshIndex),
                        allocations[modelComponent]!!.forMeshes[meshIndex].indexOffset,
                        allocations[modelComponent]!!.forMeshes[meshIndex].vertexOffset,
                        !modelComponent.model.isStatic, entity.instanceMinMaxWorlds, mesh.material.materialInfo, entity.index, meshIndex)

                if (batch.isStatic) {
                    currentWriteState.addStatic(batch)
                } else {
                    currentWriteState.addAnimated(batch)
                }
            }
        }
    }

}