package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.ComponentMapper
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.instanceCount
import de.hanno.hpengine.engine.scene.BatchKey
import de.hanno.hpengine.engine.scene.Scene
import org.joml.FrustumIntersection
import org.joml.Matrix4f
import org.joml.Vector3f

class BatchingSystem(engine: Engine, scene: Scene): SimpleEntitySystem(engine, scene, listOf(ModelComponent::class.java)) {

    private val cameraMapper = ComponentMapper.forClass(Camera::class.java)
    private val tempDistVector = Vector3f()

    override fun update(deltaSeconds: Float) {

    }

    fun addRenderBatches(currentWriteState: RenderState) {
        val camera = engine.getScene().activeCamera
        val cameraWorldPosition = camera.position

        val firstpassDefaultProgram = engine.programManager.firstpassDefaultProgram

        addBatches(cameraMapper.getComponent(camera), currentWriteState, cameraWorldPosition, firstpassDefaultProgram, components[ModelComponent::class.java] as List<ModelComponent>)
    }

    private fun addBatches(camera: Camera, currentWriteState: RenderState, cameraWorldPosition: Vector3f, firstpassDefaultProgram: Program, modelComponents: List<ModelComponent>) {
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
                mesh.material.setTexturesUsed()

                val (min1, max1) = modelComponent.getMinMax(entity, mesh)
//                val meshIsInFrustum = camera.frustum.sphereInFrustum(meshCenter.x, meshCenter.y, meshCenter.z, boundingSphereRadius)//TODO: Fix this
                val intersectAab = camera.frustum.frustumIntersection.intersectAab(min1.x, min1.y, min1.z, max1.x, max1.y, max1.z)
                val meshIsInFrustum = intersectAab == FrustumIntersection.INTERSECT || intersectAab == FrustumIntersection.INSIDE

                val visibleForCamera = meshIsInFrustum || entity.instanceCount > 1 // TODO: Better culling for instances
                val meshBufferIndex = entityIndexOf + i * entity.instanceCount

                val batch = (currentWriteState.entitiesState.cash).computeIfAbsent(BatchKey(mesh, -1)) { (_, _) -> RenderBatch() }
                batch.init(firstpassDefaultProgram, meshBufferIndex, entity.isVisible, entity.isSelected, Config.getInstance().isDrawLines, cameraWorldPosition, isInReachForTextureLoading, entity.instanceCount, visibleForCamera, entity.update, min1, max1, meshCenter, boundingSphereRadius, modelComponent.getIndexCount(i), modelComponent.getIndexOffset(i), modelComponent.getBaseVertex(i), !modelComponent.model.isStatic, entity.instanceMinMaxWorlds)
                if (batch.isStatic) {
                    currentWriteState.addStatic(batch)
                } else {
                    currentWriteState.addAnimated(batch)
                }
            }
        }
    }

}