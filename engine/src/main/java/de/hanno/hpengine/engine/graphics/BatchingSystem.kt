package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.ComponentMapper
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.instanceCount
import de.hanno.hpengine.engine.scene.BatchKey
import de.hanno.hpengine.engine.scene.SimpleScene
import org.joml.FrustumIntersection
import org.joml.Vector3f

class BatchingSystem(engine: Engine<*>, simpleScene: SimpleScene): SimpleEntitySystem(engine, simpleScene, listOf(ModelComponent::class.java)) {

    private val cameraMapper = ComponentMapper.forClass(Camera::class.java)
    private val tempDistVector = Vector3f()

    override fun update(deltaSeconds: Float) {

    }

    override fun extract(renderState: RenderState) {
        val camera = engine.scene.activeCamera
        val cameraWorldPosition = camera.entity.position

        addBatches(camera, renderState, cameraWorldPosition, components[ModelComponent::class.java] as List<ModelComponent>)
    }

    private fun addBatches(camera: Camera, currentWriteState: RenderState, cameraWorldPosition: Vector3f, modelComponents: List<ModelComponent>) {
        for (modelComponent in modelComponents) {
            val entity = modelComponent.entity
            val distanceToCamera = tempDistVector.length()
            val isInReachForTextureLoading = distanceToCamera < 50 || distanceToCamera < 2.5f * modelComponent.boundingSphereRadius

            val entityIndexOf = entity.getComponent(ModelComponent::class.java).entityBufferIndex

            val meshes = modelComponent.meshes
            for (i in meshes.indices) {
                val mesh = meshes[i]
                val meshCenter = mesh.getCenter(entity)
                val boundingSphereRadius = modelComponent.getBoundingSphereRadius(mesh)

                val (min1, max1) = modelComponent.getMinMax(entity, mesh)
                val intersectAABB = camera.frustum.frustumIntersection.intersectAab(min1, max1)
                val meshIsInFrustum = intersectAABB == FrustumIntersection.INTERSECT || intersectAABB == FrustumIntersection.INSIDE


                val visibleForCamera = meshIsInFrustum || entity.instanceCount > 1 // TODO: Better culling for instances
                val meshBufferIndex = entityIndexOf + i * entity.instanceCount

                val batch = (currentWriteState.entitiesState.cash).computeIfAbsent(BatchKey(mesh, -1)) { (_, _) -> RenderBatch() }
                batch.init(meshBufferIndex, entity.isVisible, entity.isSelected, engine.config.debug.isDrawLines, cameraWorldPosition, isInReachForTextureLoading, entity.instanceCount, visibleForCamera, entity.update, min1, max1, meshCenter, boundingSphereRadius, modelComponent.getIndexCount(i), modelComponent.getIndexOffset(i), modelComponent.getBaseVertex(i), !modelComponent.model.isStatic, entity.instanceMinMaxWorlds, mesh.material.materialInfo)
                if (batch.isStatic) {
                    currentWriteState.addStatic(batch)
                } else {
                    currentWriteState.addAnimated(batch)
                }
            }
        }
    }

}