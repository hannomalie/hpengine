package de.hanno.hpengine.graphics.renderer.pipelines

import DrawElementsIndirectCommandStruktImpl.Companion.sizeInBytes
import EntityStruktImpl.Companion.sizeInBytes
import EntityStruktImpl.Companion.type
import IntStruktImpl.Companion.sizeInBytes
import de.hanno.hpengine.backend.OpenGl
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.AtomicCounterBuffer
import de.hanno.hpengine.graphics.renderer.IndirectCulledDrawDescription
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.drawstrategy.RenderingMode
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.ZERO_BUFFER
import de.hanno.hpengine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.model.texture.Texture2D
import de.hanno.hpengine.model.texture.Texture2D.TextureUploadInfo.Texture2DUploadInfo
import de.hanno.hpengine.model.texture.TextureDimension
import de.hanno.hpengine.model.texture.TextureManager
import de.hanno.hpengine.graphics.vertexbuffer.multiDrawElementsIndirectCount
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.util.Util
import org.jetbrains.kotlin.util.profile
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL15.GL_WRITE_ONLY

open class GPUCulledPipeline @JvmOverloads constructor(
    private val config: Config,
    private val gpuContext: GpuContext<OpenGl>,
    private val programManager: ProgramManager<OpenGl>,
    private val textureManager: TextureManager,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val useBackFaceCulling: Boolean = true,
) {
    // TODO: Fill these if possible
    private var verticesCount = 0
    private var entitiesCount = 0
    internal var commandOrganizationStatic = CommandOrganizationGpuCulled(gpuContext)
    internal var commandOrganizationAnimated = CommandOrganizationGpuCulled(gpuContext)

    private var occlusionCullingPhase1Vertex: Program<Uniforms> = config.run {
        programManager.getProgram(EngineAsset("shaders/occlusion_culling1_vertex.glsl").toCodeSource(), null)
    }
    private var occlusionCullingPhase2Vertex: Program<Uniforms> = config.run {
        programManager.getProgram(EngineAsset("shaders/occlusion_culling2_vertex.glsl").toCodeSource(), null)
    }

    val appendDrawCommandsProgram = config.run {
        programManager.getProgram(EngineAsset("shaders/append_drawcommands_vertex.glsl").toCodeSource(), null)
    }
    val appendDrawCommandsComputeProgram = config.run {
        programManager.getComputeProgram(EngineAsset("shaders/append_drawcommands_compute.glsl"))
    }

    private val baseDepthTexture = Texture2D(
        gpuContext = gpuContext,
        info = Texture2DUploadInfo(TextureDimension(config.width, config.height)),
        textureFilterConfig = TextureFilterConfig(MinFilter.NEAREST_MIPMAP_NEAREST, MagFilter.NEAREST),
        internalFormat = deferredRenderingBuffer.depthAndIndicesMap.internalFormat,
        wrapMode = GL12.GL_CLAMP_TO_EDGE,
    ).apply {
        textureManager.registerTextureForDebugOutput("High Z base depth", this)
    }

    private val debugMinMaxTexture = Texture2D(
        gpuContext = gpuContext,
        info = Texture2DUploadInfo(TextureDimension(config.width / 2, config.height / 2)),
        textureFilterConfig = TextureFilterConfig(MinFilter.NEAREST_MIPMAP_NEAREST, MagFilter.NEAREST),
        internalFormat = GL30.GL_RGBA8,
        wrapMode = GL12.GL_CLAMP_TO_EDGE,
    ).apply {
        textureManager.registerTextureForDebugOutput("Min Max Debug", this)
    }

    private val highZBuffer = RenderTarget(
        gpuContext = gpuContext,
        frameBuffer = FrameBuffer(gpuContext, null),
        width = config.width / 2,
        height = config.height / 2,
        textures = listOf(
            Texture2D(
                gpuContext = gpuContext,
                info = Texture2DUploadInfo(TextureDimension(config.width / 2, config.height / 2)),
                textureFilterConfig = TextureFilterConfig(MinFilter.NEAREST_MIPMAP_NEAREST, MagFilter.NEAREST),
                internalFormat = HIGHZ_FORMAT,
                wrapMode = GL12.GL_CLAMP_TO_EDGE,
            )
        ), name = "High Z"
    ).apply {
        gpuContext.register(this)
    }

    fun copyDepthTexture() {
        GL43.glCopyImageSubData(
            deferredRenderingBuffer.depthAndIndicesMap.id, GL13.GL_TEXTURE_2D, 0, 0, 0, 0,
            baseDepthTexture.id, GL13.GL_TEXTURE_2D, 0, 0, 0, 0,
            baseDepthTexture.dimension.width, baseDepthTexture.dimension.height, 1
        )
    }

    fun prepare(renderState: RenderState) {
        if (config.debug.freezeCulling) return

        verticesCount = 0
        entitiesCount = 0

        fun CommandOrganizationGpuCulled.prepare(batches: List<RenderBatch>) {
            // TODO: This should be abstracted into "state change needed"
            filteredRenderBatches = batches
                .filter {
                    val culled = if (config.debug.isUseCpuFrustumCulling) {
                        it.isCulled(renderState.camera)
                    } else false

                    it.canBeRenderedInIndirectBatch && !culled
                }

            commandCount = filteredRenderBatches.size
            addCommands(filteredRenderBatches, commands, offsetsForCommand)
        }

        commandOrganizationStatic.prepare(renderState.renderBatchesStatic)
        commandOrganizationAnimated.prepare(renderState.renderBatchesAnimated)
    }

    fun draw(
        renderState: RenderState,
        programStatic: Program<StaticFirstPassUniforms>,
        programAnimated: Program<AnimatedFirstPassUniforms>
    ) {
        val firstPassResult = renderState.latestDrawResult.firstPassResult
        profiled("Actual draw entities") {
            val mode = if (config.debug.isDrawLines) RenderingMode.Lines else RenderingMode.Faces

            val drawDescriptionStatic = IndirectCulledDrawDescription(
                renderState,
                programStatic,
                commandOrganizationStatic,
                renderState.vertexIndexBufferStatic,
                mode,
                renderState.camera,
                renderState.camera
            )
            val drawDescriptionAnimated = IndirectCulledDrawDescription(
                renderState,
                programAnimated,
                commandOrganizationAnimated,
                renderState.vertexIndexBufferAnimated,
                mode,
                renderState.camera,
                renderState.camera
            )

            cullAndRender(drawDescriptionStatic, drawDescriptionAnimated)

//            redundant, because it's done above as long as i skip phase two of culling
//            drawDescriptionStatic.draw()
//            drawDescriptionAnimated.draw()

            firstPassResult.verticesDrawn += verticesCount
            firstPassResult.entitiesDrawn += entitiesCount
        }
    }

    private fun cullAndRender(
        drawDescriptionStatic: IndirectCulledDrawDescription<StaticFirstPassUniforms>,
        drawDescriptionAnimated: IndirectCulledDrawDescription<AnimatedFirstPassUniforms>
    ) {
        ARBClearTexture.glClearTexImage(
            highZBuffer.renderedTexture,
            0,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            ZERO_BUFFER
        )
        profiled("Cull&Render Phase1") {
            drawDescriptionStatic.cullAndRender(CoarseCullingPhase.ONE.staticPhase)
            drawDescriptionAnimated.cullAndRender(CoarseCullingPhase.ONE.animatedPhase)
            renderHighZMap()
        }
    }

    private val highZProgram = config.run {
        programManager.getComputeProgram(
            EngineAsset("shaders/highZ_compute.glsl"),
            Defines()
        )
    }

    private fun renderHighZMap() = profile("HighZ map calculation") {
        highZProgram.use()
        var lastWidth = config.width
        var lastHeight = config.height
        var currentWidth = lastWidth / 2
        var currentHeight = lastHeight / 2
        val mipMapCount = Util.calculateMipMapCount(currentWidth, currentHeight)
        for (mipmapTarget in 0 until mipMapCount) {
            highZProgram.setUniform("width", currentWidth)
            highZProgram.setUniform("height", currentHeight)
            highZProgram.setUniform("lastWidth", lastWidth)
            highZProgram.setUniform("lastHeight", lastHeight)
            highZProgram.setUniform("mipmapTarget", mipmapTarget)
            if (mipmapTarget == 0) {
                gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, baseDepthTexture.id)
            } else {
                gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, highZBuffer.renderedTexture)
            }
            gpuContext.bindImageTexture(
                1,
                highZBuffer.renderedTexture,
                mipmapTarget,
                false,
                0,
                GL15.GL_READ_WRITE,
                HIGHZ_FORMAT
            )
            gpuContext.bindTexture(2, GlTextureTarget.TEXTURE_2D, baseDepthTexture.id)
            val num_groups_x = Math.max(1, (currentWidth + 7) / 8)
            val num_groups_y = Math.max(1, (currentHeight + 7) / 8)
            highZProgram.dispatchCompute(num_groups_x, num_groups_y, 1)
            lastWidth = currentWidth
            lastHeight = currentHeight
            currentWidth /= 2
            currentHeight /= 2
            GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT)
            //            glMemoryBarrier(GL_ALL_BARRIER_BITS);
        }
    }

    private fun determineVisibilities(
        renderState: RenderState,
        commandOrganization: CommandOrganizationGpuCulled,
        phase: CullingPhase, cullCam: Camera
    ) = profiled("Visibility detection") {
        ARBClearTexture.glClearTexImage(debugMinMaxTexture.id, 0, GL11.GL_RGBA, GL11.GL_FLOAT, ZERO_BUFFER)

        val occlusionCullingPhase =
            if (phase.coarsePhase == CoarseCullingPhase.ONE) occlusionCullingPhase1Vertex else occlusionCullingPhase2Vertex
        with(occlusionCullingPhase) {
            val invocationsPerCommand = 4096
            use()
            with(commandOrganization) {
                bindShaderStorageBuffer(1, instanceCountForCommand)
                bindShaderStorageBuffer(3, renderState.entitiesState.entitiesBuffer)
                bindShaderStorageBuffer(4, offsetsForCommand)
                bindShaderStorageBuffer(5, commands)
                bindShaderStorageBuffer(9, visibilities)
                bindShaderStorageBuffer(10, entitiesCompacted)
                bindShaderStorageBuffer(11, entitiesCompactedCounter)
                bindShaderStorageBuffer(12, commandOffsets)
                bindShaderStorageBuffer(13, currentCompactedPointers)
            }
            setUniform("maxDrawCommands", commandOrganization.commandCount)
            val camera = cullCam
            setUniformAsMatrix4("viewProjectionMatrix", camera.viewProjectionMatrixAsBuffer)
            setUniformAsMatrix4("viewMatrix", camera.viewMatrixAsBuffer)
            setUniform("camPosition", camera.transform.position)
            setUniformAsMatrix4("projectionMatrix", camera.projectionMatrixAsBuffer)
            setUniform("useFrustumCulling", config.debug.isUseGpuFrustumCulling)
            setUniform("useOcclusionCulling", config.debug.isUseGpuOcclusionCulling)
            gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, highZBuffer.renderedTexture)
//            gpuContext.bindImageTexture(1, highZBuffer.renderedTexture, 0, false, 0, GL15.GL_WRITE_ONLY, HIGHZ_FORMAT)
            gpuContext.bindImageTexture(
                1,
                debugMinMaxTexture.id,
                0,
                false,
                0,
                GL_WRITE_ONLY,
                debugMinMaxTexture.internalFormat
            )
            GL31.glDrawArraysInstanced(
                GL11.GL_TRIANGLES,
                0,
                ((commandOrganization.commandCount + 2) / 3) * 3,
                invocationsPerCommand
            )
            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT)
            GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
        }
    }

    private fun appendDrawCalls(
        commandOrganization: CommandOrganizationGpuCulled,
        renderState: RenderState
    ) {
        val drawCountBuffer = commandOrganization.drawCountsCompacted
        drawCountBuffer.put(0, 0)
        val appendProgram = if (config.debug.isUseComputeShaderDrawCommandAppend) {
            appendDrawCommandsComputeProgram
        } else appendDrawCommandsProgram

        profiled("Buffer compaction") {

            with(commandOrganization) {
                val instanceCount = commandOrganization.filteredRenderBatches.sumByLong {
                        it.drawElementsIndirectCommand.instanceCount.toLong()
                    }.toInt()
                visibilities.resize(instanceCount)
                commandOrganization.entitiesCompacted.ensureCapacityInBytes(instanceCount * EntityStrukt.sizeInBytes)
                val entitiesCountersToUse = instanceCountForCommand
                entitiesCountersToUse.resize(commandCount)
                with(appendProgram) {
                    use()
                    bindShaderStorageBuffer(1, instanceCountForCommand)
                    bindShaderStorageBuffer(2, drawCountBuffer)
                    bindShaderStorageBuffer(3, renderState.entitiesState.entitiesBuffer)
                    bindShaderStorageBuffer(4, offsetsForCommand)
                    bindShaderStorageBuffer(5, commands)
                    bindShaderStorageBuffer(7, commandsCompacted)
                    bindShaderStorageBuffer(8, offsetsCompacted)
                    bindShaderStorageBuffer(9, visibilities)
                    bindShaderStorageBuffer(12, commandOffsets)
                    bindShaderStorageBuffer(13, currentCompactedPointers)
                    setUniform("maxDrawCommands", commandCount)
                    gpuContext.bindImageTexture(
                        1,
                        debugMinMaxTexture.id,
                        0,
                        false,
                        0,
                        GL_WRITE_ONLY,
                        debugMinMaxTexture.internalFormat
                    )
                    if (config.debug.isUseComputeShaderDrawCommandAppend) {
                        appendDrawCommandsComputeProgram.dispatchCompute(commandCount, 1, 1)
                    } else {
                        val invocationsPerCommand = 4096
                        GL31.glDrawArraysInstanced(
                            GL11.GL_TRIANGLES,
                            0,
                            ((invocationsPerCommand + 2) / 3) * 3,
                            commandCount
                        )
                    }
                    GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT or GL42.GL_TEXTURE_FETCH_BARRIER_BIT or GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT or GL42.GL_COMMAND_BARRIER_BIT)
                    GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
                }
            }
        }
    }

    private fun IndirectCulledDrawDescription<out FirstPassUniforms>.cullAndRender(phase: CullingPhase) {
        commandOrganization.drawCountsCompacted.put(0, 0)
        commandOrganization.entitiesCompactedCounter.put(0, 0)

        if (commandOrganization.commandCount != 0) {
            profiled("Culling Phase") {
                determineVisibilities(renderState, commandOrganization, phase, cullCam)
                appendDrawCalls(commandOrganization, renderState)
            }
            profiled("Actually render") {

                gpuContext.cullFace = useBackFaceCulling

                program.use()
                program.uniforms.apply {
                    materials = renderState.materialBuffer
                    entities = renderState.entitiesBuffer
                    indirect = true
                    when (this) {
                        is StaticFirstPassUniforms -> vertices = renderState.vertexIndexBufferStatic.vertexStructArray
                        is AnimatedFirstPassUniforms -> {
                            joints = renderState.entitiesState.jointsBuffer
                            vertices = renderState.vertexIndexBufferAnimated.animatedVertexStructArray
                        }
                    }
                    useRainEffect = config.effects.rainEffect != 0.0f
                    rainEffect = config.effects.rainEffect
                    viewMatrix = camera.viewMatrixAsBuffer
                    lastViewMatrix = camera.viewMatrixAsBuffer
                    projectionMatrix = camera.projectionMatrixAsBuffer
                    viewProjectionMatrix = camera.viewProjectionMatrixAsBuffer

                    eyePosition = camera.getPosition()
                    near = camera.near
                    far = camera.far
                    time = renderState.time.toInt()
                    useParallax = config.quality.isUseParallax
                    useSteepParallax = config.quality.isUseSteepParallax

                    entityIndex = 0
                    entityBaseIndex = 0
                    entities = renderState.entitiesState.entitiesBuffer
                    entityOffsets = commandOrganization.offsetsForCommand
                    when (this) {
                        is StaticFirstPassUniforms -> Unit
                        is AnimatedFirstPassUniforms -> joints = renderState.entitiesState.jointsBuffer
                    }
                }

                program.bind()
                vertexIndexBuffer.multiDrawElementsIndirectCount(
                    commandOrganization.commands,
                    commandOrganization.drawCountsCompacted,
                    0,
                    commandOrganization.commandCount,
                    mode
                )
            }
        }
    }
}

val RenderBatch.canBeRenderedInIndirectBatch
    get() = !isForwardRendered && !hasOwnProgram && material.writesDepth && material.renderPriority == null


fun addCommands(
    renderBatches: List<RenderBatch>,
    commandBuffer: PersistentTypedBuffer<DrawElementsIndirectCommandStrukt>,
    entityOffsetBuffer: PersistentTypedBuffer<IntStrukt>
) {
    val resultingCommandCount = renderBatches.size
    entityOffsetBuffer.resize(resultingCommandCount * IntStrukt.sizeInBytes)
    entityOffsetBuffer.buffer.rewind()
    commandBuffer.persistentMappedBuffer.enlarge(resultingCommandCount * DrawElementsIndirectCommandStrukt.sizeInBytes)
    commandBuffer.persistentMappedBuffer.buffer.rewind()

    commandBuffer.typedBuffer.byteBuffer.run {
        for ((index, batch) in renderBatches.withIndex()) {
            val command = batch.drawElementsIndirectCommand
            commandBuffer.typedBuffer[index].run {
                count = command.count
                instanceCount = command.instanceCount
                firstIndex = command.firstIndex
                baseVertex = command.baseVertex
                baseInstance = command.baseInstance
            }
            entityOffsetBuffer.typedBuffer.forIndex(index) { it.value = batch.entityBufferIndex }
        }
    }
    entityOffsetBuffer.buffer.rewind()
    commandBuffer.typedBuffer.byteBuffer.rewind()
}

private const val HIGHZ_FORMAT = GL30.GL_R32F

enum class CoarseCullingPhase {
    ONE,
    TWO
}

enum class CullingPhase(val coarsePhase: CoarseCullingPhase) {
    STATIC_ONE(CoarseCullingPhase.ONE),
    STATIC_TWO(CoarseCullingPhase.TWO),
    ANIMATED_ONE(CoarseCullingPhase.ONE),
    ANIMATED_TWO(CoarseCullingPhase.TWO)
}

val CoarseCullingPhase.staticPhase: CullingPhase
    get() = if (this == CoarseCullingPhase.ONE) CullingPhase.STATIC_ONE else CullingPhase.STATIC_TWO
val CoarseCullingPhase.animatedPhase: CullingPhase
    get() = if (this == CoarseCullingPhase.ONE) CullingPhase.ANIMATED_ONE else CullingPhase.ANIMATED_TWO

class CommandOrganizationGpuCulled(gpuContext: GpuContext<*>) {
    var commandCount = 0
    var primitiveCount = 0
    var filteredRenderBatches: List<RenderBatch> = emptyList()
    val commands = CommandBuffer(gpuContext, 10000)
    val commandsCompacted = CommandBuffer(gpuContext, 10000)
    val offsetsForCommand = IndexBuffer(gpuContext, 10000)

    val drawCountsCompacted = AtomicCounterBuffer(gpuContext, 1)
    val visibilities = IndexBuffer(gpuContext, 10000)
    val commandOffsets = IndexBuffer(gpuContext, 10000)
    val currentCompactedPointers = IndexBuffer(gpuContext, 10000)
    val offsetsCompacted = IndexBuffer(gpuContext, 10000)
    val entitiesCompacted = PersistentMappedBuffer(EntityStrukt.type.sizeInBytes, gpuContext).typed(EntityStrukt.type)
    val entitiesCompactedCounter = AtomicCounterBuffer(gpuContext, 1)
    val instanceCountForCommand = IndexBuffer(gpuContext)
}

