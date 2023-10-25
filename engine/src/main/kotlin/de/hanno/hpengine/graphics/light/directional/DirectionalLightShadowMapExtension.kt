package de.hanno.hpengine.graphics.light.directional

import AnimatedVertexStruktPackedImpl.Companion.sizeInBytes
import AnimatedVertexStruktPackedImpl.Companion.type
import DirectionalLightStateImpl.Companion.type
import EntityStruktImpl.Companion.type
import IntStruktImpl.Companion.type
import InternalTextureFormat
import MaterialStruktImpl.Companion.type
import Matrix4fStruktImpl.Companion.sizeInBytes
import Matrix4fStruktImpl.Companion.type
import VertexStruktPackedImpl.Companion.type
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.pipelines.IntStrukt
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.graphics.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.graphics.rendertarget.OpenGLFrameBuffer
import de.hanno.hpengine.graphics.rendertarget.toTextures
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.EntitiesState
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.math.Matrix4fStrukt
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.model.material.MaterialStrukt
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.scene.AnimatedVertexStruktPacked
import de.hanno.hpengine.scene.VertexStruktPacked
import de.hanno.hpengine.system.Extractor
import org.joml.Vector4f
import org.koin.core.annotation.Single
import struktgen.api.forIndex


@Single(binds = [DirectionalLightShadowMapExtension::class, Extractor::class, RenderSystem::class])
class DirectionalLightShadowMapExtension(
    private val graphicsApi: GraphicsApi,
    renderStateContext: RenderStateContext,
    config: Config,
    programManager: ProgramManager,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
) : Extractor, RenderSystem {

    private var forceRerender = true

    val renderTarget = graphicsApi.RenderTarget(
        OpenGLFrameBuffer(graphicsApi, graphicsApi.DepthBuffer(SHADOWMAP_RESOLUTION, SHADOWMAP_RESOLUTION)),
        SHADOWMAP_RESOLUTION,
        SHADOWMAP_RESOLUTION,
//                Reflective shadowmaps?
//                .add(new ColorAttachmentDefinitions(new String[]{"Shadow", "Shadow", "Shadow"}, GL30.GL_RGBA32F))
        listOf(ColorAttachmentDefinition("Shadow", InternalTextureFormat.RGBA16F, TextureFilterConfig(MinFilter.LINEAR))).toTextures(
            graphicsApi,
            SHADOWMAP_RESOLUTION,
            SHADOWMAP_RESOLUTION
        ),
        "DirectionalLight Shadow",
        Vector4f(1f, 1f, 1f, 1f),
    ).apply {
        factorsForDebugRendering[0] = 100f
    }

    private val staticDirectionalShadowPassProgram = programManager.getProgram(
        FileBasedCodeSource(config.engineDir.resolve("shaders/directional_shadowmap_vertex.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/shadowmap_fragment.glsl")),
        StaticDirectionalShadowUniforms(graphicsApi),
        Defines()
    )
    private val animatedDirectionalShadowPassProgram = programManager.getProgram(
        FileBasedCodeSource(config.engineDir.resolve("shaders/directional_shadowmap_vertex.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/shadowmap_fragment.glsl")),
        AnimatedDirectionalShadowUniforms(graphicsApi),
        Defines(Define("ANIMATED", true)),
    )

    private val staticPipeline = renderStateContext.renderState.registerState {
        DirectionalShadowMapPipeline(staticDirectionalShadowPassProgram)
    }
    private val animatedPipeline = renderStateContext.renderState.registerState {
        DirectionalShadowMapPipeline(animatedDirectionalShadowPassProgram)
    }

    inner class DirectionalShadowMapPipeline(val directionalShadowPassProgram: Program<out DirectionalShadowUniforms>) {
        private var verticesCount = 0
        private var entitiesCount = 0

        fun draw(renderState: RenderState) = graphicsApi.run {
            profiled("Actual draw entities") {
                verticesCount = 0
                entitiesCount = 0
                val entitiesState = renderState[entitiesStateHolder.entitiesState]
                val program = directionalShadowPassProgram
                val vertexIndexBuffer = entitiesState.selectVertexIndexBuffer(program.uniforms)

                vertexIndexBuffer.indexBuffer.bind()

                program.use()
                program.uniforms.apply {
                    materials = entitiesState.materialBuffer
                    directionalLightState = renderState[directionalLightStateHolder.lightState]
                    entities = entitiesState.entitiesBuffer
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

                val shadowCasters = entitiesState.getRenderBatches(program.uniforms).filter { it.isShadowCasting }
                for (batch in shadowCasters) {
                    program.uniforms.entityIndex = batch.entityBufferIndex
                    program.bind()
                    vertexIndexBuffer.indexBuffer.draw(batch.drawElementsIndirectCommand, false, PrimitiveType.Triangles, RenderingMode.Fill)
                    verticesCount += batch.vertexCount
                    entitiesCount += 1
                }
            }
        }

        private fun EntitiesState.getRenderBatches(uniforms: DirectionalShadowUniforms) = when (uniforms) {
            is AnimatedDirectionalShadowUniforms -> renderBatchesAnimated
            is StaticDirectionalShadowUniforms -> renderBatchesStatic
        }.filter { it.isVisible && it.isShadowCasting }

        private fun EntitiesState.selectVertexIndexBuffer(uniforms: DirectionalShadowUniforms) = when (uniforms) {
            is AnimatedDirectionalShadowUniforms -> vertexIndexBufferAnimated
            is StaticDirectionalShadowUniforms -> vertexIndexBufferStatic
        }
    }

    private var renderedInCycle: Long = 0

    val shadowMapId = renderTarget.renderedTexture

    override fun extract(currentWriteState: RenderState) {
        currentWriteState[directionalLightStateHolder.lightState].typedBuffer.forIndex(0) {
            it.shadowMapHandle = renderTarget.frameBuffer.depthBuffer!!.texture.handle
            it.shadowMapId = renderTarget.frameBuffer.depthBuffer!!.texture.id
        }
    }

    override fun render(renderState: RenderState) = graphicsApi.run {
        val entitiesState = renderState[entitiesStateHolder.entitiesState]
        profiled("Directional shadowmap") {
            val needsRerender = forceRerender ||
                    renderedInCycle < renderState[directionalLightStateHolder.directionalLightHasMovedInCycle] ||
                    renderedInCycle < entitiesState.anyEntityMovedInCycle ||
                    renderedInCycle < entitiesState.entityAddedInCycle ||
                    entitiesState.renderBatchesAnimated.isNotEmpty()

            if (needsRerender) {
                drawShadowMap(renderState)
            }
        }
    }

    private fun drawShadowMap(renderState: RenderState) = graphicsApi.run {
        blend = false
        depthMask = true
        depthTest = true
        depthFunc = DepthFunc.LESS
        cullFace = false
        renderTarget.use(true)

        renderState[staticPipeline].draw(renderState)
        renderState[animatedPipeline].draw(renderState)

        generateMipMaps(renderTarget.textures[0])

        renderedInCycle = renderState.cycle
        forceRerender = false
    }

    companion object {
        const val SHADOWMAP_RESOLUTION = 2048
    }
}

sealed class DirectionalShadowUniforms(graphicsApi: GraphicsApi) : Uniforms() {
    var materials by SSBO("Material", 1, graphicsApi.PersistentShaderStorageBuffer(1).typed(MaterialStrukt.type))
    var directionalLightState by SSBO(
        "DirectionalLightState", 2, graphicsApi.PersistentShaderStorageBuffer(1).typed(DirectionalLightState.type)
    )
    var entities by SSBO("Entity", 3, graphicsApi.PersistentShaderStorageBuffer(1).typed(EntityStrukt.type))
    var entityOffsets by SSBO("int", 4, graphicsApi.PersistentShaderStorageBuffer(1).typed(IntStrukt.type))

    var indirect by BooleanType(true)
    var entityIndex by IntType(0)
    var entityBaseIndex by IntType(0)
}

class AnimatedDirectionalShadowUniforms(graphicsApi: GraphicsApi) : DirectionalShadowUniforms(graphicsApi) {
    var joints by SSBO(
        "mat4",
        6,
        graphicsApi.PersistentShaderStorageBuffer(Matrix4fStrukt.sizeInBytes).typed(Matrix4fStrukt.type)
    )
    var vertices by SSBO(
        "VertexAnimatedPacked", 7, graphicsApi.PersistentShaderStorageBuffer(
            AnimatedVertexStruktPacked.sizeInBytes
        ).typed(AnimatedVertexStruktPacked.type)
    )
}

class StaticDirectionalShadowUniforms(graphicsApi: GraphicsApi) : DirectionalShadowUniforms(graphicsApi) {
    var vertices by SSBO("VertexPacked", 7, graphicsApi.PersistentShaderStorageBuffer(1).typed(VertexStruktPacked.type))
}