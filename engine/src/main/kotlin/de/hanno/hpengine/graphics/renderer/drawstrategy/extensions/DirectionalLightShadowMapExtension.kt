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
import de.hanno.hpengine.artemis.EntitiesStateHolder

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.RenderStateContext
import de.hanno.hpengine.graphics.light.directional.DirectionalLightStateHolder
import de.hanno.hpengine.graphics.light.directional.DirectionalLightSystem
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.constants.DepthFunc
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget.TEXTURE_2D
import de.hanno.hpengine.graphics.renderer.drawstrategy.PrimitiveType
import de.hanno.hpengine.graphics.renderer.drawstrategy.RenderingMode
import de.hanno.hpengine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.graphics.renderer.pipelines.*
import de.hanno.hpengine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.graphics.renderer.rendertarget.DepthBuffer
import de.hanno.hpengine.graphics.renderer.rendertarget.OpenGLFrameBuffer
import de.hanno.hpengine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.graphics.renderer.rendertarget.toTextures
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.DirectionalLightState
import de.hanno.hpengine.graphics.state.EntitiesState
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.math.Matrix4fStrukt
import de.hanno.hpengine.model.material.MaterialStrukt
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.scene.AnimatedVertexStruktPacked
import de.hanno.hpengine.scene.VertexStruktPacked
import org.joml.Vector4f
import org.lwjgl.opengl.GL30

context(GpuContext, RenderStateContext)
class DirectionalLightShadowMapExtension(
    private val config: Config,
    private val programManager: ProgramManager,
    private val textureManager: OpenGLTextureManager,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
) : DeferredRenderExtension {

    private var forceRerender = true

    val renderTarget = RenderTarget(
        OpenGLFrameBuffer(DepthBuffer(SHADOWMAP_RESOLUTION, SHADOWMAP_RESOLUTION)),
        SHADOWMAP_RESOLUTION,
        SHADOWMAP_RESOLUTION,
        //                Reflective shadowmaps?
        //                .add(new ColorAttachmentDefinitions(new String[]{"Shadow", "Shadow", "Shadow"}, GL30.GL_RGBA32F))
        listOf(ColorAttachmentDefinition("Shadow", GL30.GL_RGBA16F)).toTextures(
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
        StaticDirectionalShadowUniforms(),
        Defines()
    )
    private val animatedDirectionalShadowPassProgram = programManager.getProgram(
        FileBasedCodeSource(config.engineDir.resolve("shaders/directional_shadowmap_vertex.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/shadowmap_fragment.glsl")),
        AnimatedDirectionalShadowUniforms(),
        Defines(Define("ANIMATED", true)),
    )

    private val staticPipeline = renderState.registerState {
        DirectionalShadowMapPipeline(staticDirectionalShadowPassProgram)
    }
    private val animatedPipeline = renderState.registerState {
        DirectionalShadowMapPipeline(animatedDirectionalShadowPassProgram)
    }

    inner class DirectionalShadowMapPipeline(val directionalShadowPassProgram: IProgram<out DirectionalShadowUniforms>) {
        private var verticesCount = 0
        private var entitiesCount = 0

        fun draw(renderState: RenderState) = profiled("Actual draw entities") {
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
                        vertices = vertexIndexBuffer.animatedVertexStructArray
                        joints = entitiesState.jointsBuffer
                    }
                }
                entityBaseIndex = 0
                indirect = false
            }

            for (batch in entitiesState.getRenderBatches(program.uniforms).filter { it.isShadowCasting }) {
                program.uniforms.entityIndex = batch.entityBufferIndex
                program.bind()
                vertexIndexBuffer.indexBuffer.draw(batch.drawElementsIndirectCommand, false, PrimitiveType.Triangles, RenderingMode.Faces)
                verticesCount += batch.vertexCount
                entitiesCount += 1
            }

            renderState.latestDrawResult.firstPassResult.verticesDrawn += verticesCount
            renderState.latestDrawResult.firstPassResult.entitiesDrawn += entitiesCount
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

    override fun extract(renderState: RenderState, world: World) {
        renderState[directionalLightStateHolder.lightState].typedBuffer.forIndex(0) {
            it.shadowMapHandle = renderTarget.renderedTextureHandles[0]
            it.shadowMapId = renderTarget.renderedTextures[0]
        }
    }

    override fun renderZeroPass(renderState: RenderState) {
        val entitiesState = renderState[entitiesStateHolder.entitiesState]
        profiled("Directional shadowmap") {
            val needsRerender = forceRerender ||
                    renderedInCycle < renderState.directionalLightHasMovedInCycle ||
                    renderedInCycle < entitiesState.anyEntityMovedInCycle ||
                    renderedInCycle < entitiesState.entityAddedInCycle ||
                    entitiesState.renderBatchesAnimated.isNotEmpty()

            if (needsRerender) {
                drawShadowMap(renderState)
            }
        }
    }

    private fun drawShadowMap(renderState: RenderState) {
        blend = false
        depthMask = true
        depthTest = true
        depthFunc = DepthFunc.LESS
        cullFace = false
        renderTarget.use(true)

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

context(GpuContext)
sealed class DirectionalShadowUniforms() : Uniforms() {
    var materials by SSBO("Material", 1, PersistentMappedBuffer(1).typed(MaterialStrukt.type))
    var directionalLightState by SSBO(
        "DirectionalLightState", 2, PersistentMappedBuffer(1).typed(DirectionalLightState.type)
    )
    var entities by SSBO("Entity", 3, PersistentMappedBuffer(1).typed(EntityStrukt.type))
    var entityOffsets by SSBO("int", 4, PersistentMappedBuffer(1).typed(IntStrukt.type))

    var indirect by BooleanType(true)
    var entityIndex by IntType(0)
    var entityBaseIndex by IntType(0)
}

context(GpuContext)
class AnimatedDirectionalShadowUniforms : DirectionalShadowUniforms() {
    var joints by SSBO(
        "mat4",
        6,
        PersistentMappedBuffer(Matrix4fStrukt.sizeInBytes).typed(Matrix4fStrukt.type)
    )
    var vertices by SSBO(
        "VertexAnimatedPacked", 7, PersistentMappedBuffer(
            AnimatedVertexStruktPacked.sizeInBytes
        ).typed(AnimatedVertexStruktPacked.type)
    )
}

context(GpuContext)
class StaticDirectionalShadowUniforms : DirectionalShadowUniforms() {
    var vertices by SSBO("VertexPacked", 7, PersistentMappedBuffer(1).typed(VertexStruktPacked.type))
}