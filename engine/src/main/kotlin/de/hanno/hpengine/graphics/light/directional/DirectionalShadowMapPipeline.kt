package de.hanno.hpengine.graphics.light.directional

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

class DirectionalShadowMapPipeline(
    private val graphicsApi: GraphicsApi,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val materialSystem: MaterialSystem,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val entityBuffer: EntityBuffer,
    private val defaultBatchesSystem: DefaultBatchesSystem,
    private val directionalShadowPassProgram: Program<DirectionalShadowUniforms>,
) {
    private var verticesCount = 0
    private var entitiesCount = 0

    fun draw(renderState: RenderState, update: Update) = graphicsApi.run {
        profiled("Actual draw entities") {
            verticesCount = 0
            entitiesCount = 0
            val entitiesState = renderState[entitiesStateHolder.entitiesState]
            val program = directionalShadowPassProgram
            val vertexIndexBuffer = entitiesState.selectVertexIndexBuffer(program.uniforms)

            vertexIndexBuffer.indexBuffer.bind()

            program.use()
            program.uniforms.apply {
                materials = renderState[materialSystem.materialBuffer]
                directionalLightState = renderState[directionalLightStateHolder.lightState]
                entities = renderState[entityBuffer.entitiesBuffer]
                when (this) {
                    is StaticDirectionalShadowUniforms -> vertices = vertexIndexBuffer.vertexStructArray
                    is AnimatedDirectionalShadowUniforms -> {
                        vertices = vertexIndexBuffer.vertexStructArray
                        joints = entitiesState.jointsBuffer
                    }
                }
                entityBaseIndex = 0
                indirect = false
            }

            val shadowCasters = defaultBatchesSystem.getRenderBatches(renderState, program.uniforms)
            for (batch in shadowCasters.filter { it.update == update }) {
                program.uniforms.entityIndex = batch.entityBufferIndex
                program.bind()
                vertexIndexBuffer.indexBuffer.draw(
                    batch.drawElementsIndirectCommand,
                    false,
                    PrimitiveType.Triangles,
                    RenderingMode.Fill
                )
                verticesCount += batch.vertexCount
                entitiesCount += 1
            }
        }
    }

    private fun DefaultBatchesSystem.getRenderBatches(renderState: RenderState, uniforms: DirectionalShadowUniforms) = when (uniforms) {
        is AnimatedDirectionalShadowUniforms -> renderState[renderBatchesAnimated]
        is StaticDirectionalShadowUniforms -> renderState[renderBatchesStatic]
    }.filter { it.isVisible && it.isShadowCasting }

    private fun EntitiesState.selectVertexIndexBuffer(uniforms: DirectionalShadowUniforms) = when (uniforms) {
        is AnimatedDirectionalShadowUniforms -> vertexIndexBufferAnimated
        is StaticDirectionalShadowUniforms -> vertexIndexBufferStatic
    }
}