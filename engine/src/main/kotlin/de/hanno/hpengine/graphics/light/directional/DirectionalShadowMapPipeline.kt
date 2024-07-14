package de.hanno.hpengine.graphics.light.directional

import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.camera.Frustum
import de.hanno.hpengine.toCount
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.PrimitiveType
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.state.EntitiesState
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.Update
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.scene.VertexBuffer
import de.hanno.hpengine.scene.VertexIndexBuffer
import org.joml.Vector3f

class DirectionalShadowMapPipeline(
    private val graphicsApi: GraphicsApi,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val materialSystem: MaterialSystem,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val entityBuffer: EntityBuffer,
    private val defaultBatchesSystem: DefaultBatchesSystem,
    private val directionalShadowPassProgram: Program<DirectionalShadowUniforms>,
) {
    private var verticesCount = ElementCount(0)
    private var entitiesCount = ElementCount(0)

    fun draw(renderState: RenderState, update: Update) = graphicsApi.run {
        profiled("Actual draw entities") {
            verticesCount = 0.toCount()
            entitiesCount = 0.toCount()
            val entitiesState = renderState[entitiesStateHolder.entitiesState]
            val program = directionalShadowPassProgram
            val geometryBuffer = entitiesState.selectVertexIndexBuffer(program.uniforms)

            when(geometryBuffer) {
                is VertexBuffer -> { }
                is VertexIndexBuffer -> geometryBuffer.indexBuffer.bind()
            }

            program.use()
            program.uniforms.apply {
                materials = renderState[materialSystem.materialBuffer]
                directionalLightState = renderState[directionalLightStateHolder.lightState]
                entities = renderState[entityBuffer.entitiesBuffer]
                when (this) {
                    is StaticDirectionalShadowUniforms -> vertices = geometryBuffer.vertexStructArray
                    is AnimatedDirectionalShadowUniforms -> {
                        vertices = geometryBuffer.vertexStructArray
                        joints = entitiesState.jointsBuffer
                    }
                }
                entityBaseIndex = 0
                indirect = false
            }

            val shadowCasters = defaultBatchesSystem.getRenderBatches(renderState, program.uniforms)
                .filter { it.update == update }
                .filter { renderState[directionalLightStateHolder.camera].frustum.contains(it.centerWorld, it.boundingSphereRadius) }

            for (batch in shadowCasters) {
                program.uniforms.entityIndex = batch.entityBufferIndex
                program.bind()
                geometryBuffer.draw(
                    batch.drawElementsIndirectCommand,
                    false,
                    PrimitiveType.Triangles,
                    RenderingMode.Fill
                )
                verticesCount += batch.vertexCount
                entitiesCount += 1.toCount()
            }
        }
    }

    private fun DefaultBatchesSystem.getRenderBatches(renderState: RenderState, uniforms: DirectionalShadowUniforms) = when (uniforms) {
        is AnimatedDirectionalShadowUniforms -> renderState[renderBatchesAnimated]
        is StaticDirectionalShadowUniforms -> renderState[renderBatchesStatic]
    }.filter { it.isVisible && it.isShadowCasting }

    private fun EntitiesState.selectVertexIndexBuffer(uniforms: DirectionalShadowUniforms) = when (uniforms) {
        is AnimatedDirectionalShadowUniforms -> geometryBufferAnimated
        is StaticDirectionalShadowUniforms -> geometryBufferStatic
    }
}

private fun Frustum.contains(center: Vector3f, radius: Float): Boolean = sphereInFrustum(center.x, center.y, center.z, radius)
