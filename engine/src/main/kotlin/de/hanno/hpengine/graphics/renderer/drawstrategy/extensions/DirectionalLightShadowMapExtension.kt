package de.hanno.hpengine.graphics.renderer.drawstrategy.extensions

import AnimatedVertexStruktPackedImpl.Companion.sizeInBytes
import AnimatedVertexStruktPackedImpl.Companion.type
import DirectionalLightStateImpl.Companion.type
import EntityStruktImpl.Companion.type
import IntStruktImpl.Companion.type
import MaterialStruktImpl.Companion.type
import Matrix4fStruktImpl.Companion.sizeInBytes
import Matrix4fStruktImpl.Companion.type
import VertexStruktPackedImpl.Companion.type
import com.artemis.World
import de.hanno.hpengine.backend.OpenGl
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.RenderStateManager
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.constants.DepthFunc
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget.TEXTURE_2D
import de.hanno.hpengine.graphics.renderer.drawstrategy.PrimitiveType
import de.hanno.hpengine.graphics.renderer.drawstrategy.RenderingMode
import de.hanno.hpengine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.graphics.renderer.pipelines.*
import de.hanno.hpengine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.graphics.renderer.rendertarget.DepthBuffer
import de.hanno.hpengine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.graphics.renderer.rendertarget.toTextures
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.DirectionalLightState
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.math.Matrix4fStrukt
import de.hanno.hpengine.model.material.MaterialStrukt
import de.hanno.hpengine.model.texture.TextureManager
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.scene.AnimatedVertexStruktPacked
import de.hanno.hpengine.scene.VertexStruktPacked
import org.joml.Vector4f
import org.lwjgl.opengl.GL30

class DirectionalLightShadowMapExtension(
    val config: Config,
    val programManager: ProgramManager<OpenGl>,
    val textureManager: TextureManager,
    val gpuContext: GpuContext<OpenGl>,
    val renderStateManager: RenderStateManager,
) : DeferredRenderExtension<OpenGl> {

    private var forceRerender = true

    val renderTarget = RenderTarget(
        gpuContext = gpuContext,
        frameBuffer = FrameBuffer(gpuContext, DepthBuffer(gpuContext, SHADOWMAP_RESOLUTION, SHADOWMAP_RESOLUTION)),
        name = "DirectionalLight Shadow",
        width = SHADOWMAP_RESOLUTION,
        height = SHADOWMAP_RESOLUTION,
        clear = Vector4f(1f, 1f, 1f, 1f),
        //                Reflective shadowmaps?
        //                .add(new ColorAttachmentDefinitions(new String[]{"Shadow", "Shadow", "Shadow"}, GL30.GL_RGBA32F))
        textures = listOf(ColorAttachmentDefinition("Shadow", GL30.GL_RGBA16F)).toTextures(
            gpuContext,
            SHADOWMAP_RESOLUTION,
            SHADOWMAP_RESOLUTION
        )
    ).apply {
        factorsForDebugRendering[0] = 100f
    }

    private val staticDirectionalShadowPassProgram = programManager.getProgram(
        FileBasedCodeSource(config.engineDir.resolve("shaders/directional_shadowmap_vertex.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/shadowmap_fragment.glsl")),
        StaticDirectionalShadowUniforms(gpuContext),
        Defines()
    )
    private val animatedDirectionalShadowPassProgram = programManager.getProgram(
        FileBasedCodeSource(config.engineDir.resolve("shaders/directional_shadowmap_vertex.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/shadowmap_fragment.glsl")),
        AnimatedDirectionalShadowUniforms(gpuContext),
        Defines(Define("ANIMATED", true)),
    )

    private val staticPipeline = renderStateManager.renderState.registerState {
        DirectionalShadowMapPipeline(staticDirectionalShadowPassProgram)
    }
    private val animatedPipeline = renderStateManager.renderState.registerState {
        DirectionalShadowMapPipeline(animatedDirectionalShadowPassProgram)
    }

    inner class DirectionalShadowMapPipeline(val directionalShadowPassProgram: IProgram<out DirectionalShadowUniforms>) {
        private var verticesCount = 0
        private var entitiesCount = 0

        fun draw(renderState: RenderState) = profiled("Actual draw entities") {
            verticesCount = 0
            entitiesCount = 0
            val program = directionalShadowPassProgram
            val vertexIndexBuffer = renderState.selectVertexIndexBuffer(program.uniforms)

            vertexIndexBuffer.indexBuffer.bind()

            program.use()
            program.uniforms.apply {
                materials = renderState.materialBuffer
                directionalLightState = renderState.directionalLightState
                entities = renderState.entitiesBuffer
                when (this) {
                    is StaticDirectionalShadowUniforms -> vertices = vertexIndexBuffer.vertexStructArray
                    is AnimatedDirectionalShadowUniforms -> {
                        vertices = vertexIndexBuffer.animatedVertexStructArray
                        joints = renderState.entitiesState.jointsBuffer
                    }
                }
                entityBaseIndex = 0
                indirect = false
            }

            for (batch in renderState.getRenderBatches(program.uniforms).filter { it.isShadowCasting }) {
                program.uniforms.entityIndex = batch.entityBufferIndex
                program.bind()
                vertexIndexBuffer.indexBuffer.draw(batch.drawElementsIndirectCommand, false, PrimitiveType.Triangles, RenderingMode.Faces)
                verticesCount += batch.vertexCount
                entitiesCount += 1
            }

            renderState.latestDrawResult.firstPassResult.verticesDrawn += verticesCount
            renderState.latestDrawResult.firstPassResult.entitiesDrawn += entitiesCount
        }

        private fun RenderState.getRenderBatches(uniforms: DirectionalShadowUniforms) = when (uniforms) {
            is AnimatedDirectionalShadowUniforms -> renderBatchesAnimated
            is StaticDirectionalShadowUniforms -> renderBatchesStatic
        }

        private fun RenderState.selectVertexIndexBuffer(uniforms: DirectionalShadowUniforms) = when (uniforms) {
            is AnimatedDirectionalShadowUniforms -> entitiesState.vertexIndexBufferAnimated
            is StaticDirectionalShadowUniforms -> entitiesState.vertexIndexBufferStatic
        }
    }

    private var renderedInCycle: Long = 0

    val shadowMapId = renderTarget.renderedTexture

    override fun extract(renderState: RenderState, world: World) {
        renderState.directionalLightState.typedBuffer.forIndex(0) {
            it.shadowMapHandle = renderTarget.renderedTextureHandles[0]
            it.shadowMapId = renderTarget.renderedTextures[0]
        }
    }

    override fun renderZeroPass(renderState: RenderState) {
        profiled("Directional shadowmap") {
            val needsRerender = forceRerender ||
                    renderedInCycle < renderState.directionalLightHasMovedInCycle ||
                    renderedInCycle < renderState.entitiesState.anyEntityMovedInCycle ||
                    renderedInCycle < renderState.entitiesState.entityAddedInCycle ||
                    renderState.entitiesState.renderBatchesAnimated.isNotEmpty()

            if (needsRerender) {
                drawShadowMap(renderState)
            }
        }
    }

    private fun drawShadowMap(renderState: RenderState) {
        gpuContext.blend = false
        gpuContext.depthMask = true
        gpuContext.depthTest = true
        gpuContext.depthFunc = DepthFunc.LESS
        gpuContext.cullFace = false
        renderTarget.use(gpuContext, true)

        renderState[staticPipeline].draw(renderState)
        renderState[animatedPipeline].draw(renderState)

        textureManager.generateMipMaps(TEXTURE_2D, shadowMapId)

        renderedInCycle = renderState.cycle
        forceRerender = false
    }

    companion object {
        const val SHADOWMAP_RESOLUTION = 2048
    }
}

sealed class DirectionalShadowUniforms(gpuContext: GpuContext<OpenGl>) : Uniforms() {
    var materials by SSBO("Material", 1, PersistentMappedBuffer(1, gpuContext).typed(MaterialStrukt.type))
    var directionalLightState by SSBO(
        "DirectionalLightState", 2, PersistentMappedBuffer(1, gpuContext).typed(DirectionalLightState.type)
    )
    var entities by SSBO("Entity", 3, PersistentMappedBuffer(1, gpuContext).typed(EntityStrukt.type))
    var entityOffsets by SSBO("int", 4, PersistentMappedBuffer(1, gpuContext).typed(IntStrukt.type))

    var indirect by BooleanType(true)
    var entityIndex by IntType(0)
    var entityBaseIndex by IntType(0)
}

class AnimatedDirectionalShadowUniforms(gpuContext: GpuContext<OpenGl>) : DirectionalShadowUniforms(gpuContext) {
    var joints by SSBO(
        "mat4",
        6,
        PersistentMappedBuffer(Matrix4fStrukt.sizeInBytes, gpuContext).typed(Matrix4fStrukt.type)
    )
    var vertices by SSBO(
        "VertexAnimatedPacked", 7, PersistentMappedBuffer(
            AnimatedVertexStruktPacked.sizeInBytes, gpuContext
        ).typed(AnimatedVertexStruktPacked.type)
    )
}

class StaticDirectionalShadowUniforms(gpuContext: GpuContext<OpenGl>) : DirectionalShadowUniforms(gpuContext) {
    var vertices by SSBO("VertexPacked", 7, PersistentMappedBuffer(1, gpuContext).typed(VertexStruktPacked.type))
}