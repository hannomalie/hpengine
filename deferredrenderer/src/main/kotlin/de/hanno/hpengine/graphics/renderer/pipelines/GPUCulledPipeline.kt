package de.hanno.hpengine.graphics.renderer.pipelines

import DrawElementsIndirectCommandStruktImpl.Companion.sizeInBytes
import EntityStruktImpl.Companion.sizeInBytes
import EntityStruktImpl.Companion.type
import IntStruktImpl.Companion.sizeInBytes
import InternalTextureFormat.*
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder

import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.Access
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.AtomicCounterBuffer
import de.hanno.hpengine.graphics.buffer.TypedGpuBuffer
import de.hanno.hpengine.graphics.buffer.vertex.drawArraysIndirectCount
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.TextureDimension
import de.hanno.hpengine.graphics.buffer.vertex.drawElementsIndirectCount
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.forward.AnimatedDefaultUniforms
import de.hanno.hpengine.graphics.renderer.forward.DefaultUniforms
import de.hanno.hpengine.graphics.renderer.forward.StaticDefaultUniforms
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.texture.TextureManager
import de.hanno.hpengine.graphics.texture.UploadInfo.SingleMipLevelTexture2DUploadInfo
import de.hanno.hpengine.graphics.texture.calculateMipMapCount
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.renderer.DrawElementsIndirectCommandStrukt
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.scene.GeometryBuffer
import de.hanno.hpengine.scene.VertexBuffer
import de.hanno.hpengine.scene.VertexIndexBuffer
import de.hanno.hpengine.toCount
import org.jetbrains.kotlin.util.profile
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import org.joml.Vector4f
import struktgen.api.forIndex
import struktgen.api.get
import kotlin.math.max

val textureFilterConfig = TextureFilterConfig(MinFilter.NEAREST_MIPMAP_NEAREST, MagFilter.NEAREST)

open class GPUCulledPipeline(
    private val graphicsApi: GraphicsApi,
    private val config: Config,
    private val programManager: ProgramManager,
    private val textureManager: TextureManager,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val useBackFaceCulling: Boolean = true,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val entityBuffer: EntityBuffer,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val defaultBatchesSystem: DefaultBatchesSystem,
    private val materialSystem: MaterialSystem,
) {
    // TODO: Fill these if possible
    private var verticesCount = 0
    private var entitiesCount = 0
    val commandOrganizationStatic = CommandOrganizationGpuCulled(graphicsApi)
    val commandOrganizationAnimated = CommandOrganizationGpuCulled(graphicsApi)

    private var occlusionCullingPhase1Vertex = config.run {
        programManager.getProgram(EngineAsset("shaders/occlusion_culling1_vertex.glsl").toCodeSource(), null)
    }
    private var occlusionCullingPhase2Vertex = config.run {
        programManager.getProgram(EngineAsset("shaders/occlusion_culling2_vertex.glsl").toCodeSource(), null)
    }

    val appendDrawCommandsProgram = config.run {
        programManager.getProgram(EngineAsset("shaders/append_drawcommands_vertex.glsl").toCodeSource(), null)
    }
    val appendDrawCommandsComputeProgram = config.run {
        programManager.getComputeProgram(EngineAsset("shaders/append_drawcommands_compute.glsl"))
    }

    private val baseDepthTexture = graphicsApi.Texture2D(
        SingleMipLevelTexture2DUploadInfo(
            data = null,
            dimension = TextureDimension(config.width, config.height),
            internalFormat = RGBA16F,
            textureFilterConfig = textureFilterConfig,
        ),
        wrapMode = WrapMode.ClampToEdge,
    ).apply {
        textureManager.registerTextureForDebugOutput("High Z base depth", this)
    }

    private val debugMinMaxTexture = graphicsApi.Texture2D(
        info = SingleMipLevelTexture2DUploadInfo(
            data = null,
            dimension = TextureDimension(config.width / 2, config.height / 2),
            internalFormat = RGBA16F,
            textureFilterConfig = textureFilterConfig,
        ),
        wrapMode = WrapMode.ClampToEdge,
    ).apply {
        textureManager.registerTextureForDebugOutput("Min Max Debug", this)
    }

    private val highZBuffer = graphicsApi.RenderTarget(
        frameBuffer = graphicsApi.FrameBuffer(null),
        width = config.width / 2,
        height = config.height / 2,
        textures = listOf(
            graphicsApi.Texture2D(
                info = SingleMipLevelTexture2DUploadInfo(
                    data = null,
                    dimension = TextureDimension(config.width / 2, config.height / 2),
                    internalFormat = RGBA16F,
                    textureFilterConfig = textureFilterConfig,
                ),
                wrapMode = WrapMode.ClampToEdge,
            )
        ),
        name = "High Z",
        clear = Vector4f(),
    ).apply {
        graphicsApi.register(this)
    }

    fun copyDepthTexture() {
        graphicsApi.copyImageSubData(
            deferredRenderingBuffer.depthAndIndicesMap, 0, 0, 0, 0,
            baseDepthTexture, 0, 0, 0, 0,
            baseDepthTexture.dimension.width, baseDepthTexture.dimension.height, 1
        )
    }

    fun prepare(renderState: RenderState) {
        if (config.debug.freezeCulling) return
        val camera = renderState[primaryCameraStateHolder.camera]

        verticesCount = 0
        entitiesCount = 0

        fun CommandOrganizationGpuCulled.prepare(batches: List<RenderBatch>) {
            // TODO: This should be abstracted into "state change needed"
            filteredRenderBatches = batches
                .filter {
                    val culled = if (config.debug.isUseCpuFrustumCulling) {
                        it.isCulled(camera)
                    } else false

                    it.canBeRenderedInIndirectBatch && !culled
                }

            commandCount = filteredRenderBatches.size.toCount()
            addCommands(filteredRenderBatches, commands, offsetsForCommand)
        }

        commandOrganizationStatic.prepare(renderState[defaultBatchesSystem.renderBatchesStatic])
        commandOrganizationAnimated.prepare(renderState[defaultBatchesSystem.renderBatchesAnimated])
    }

    fun draw(
        renderState: RenderState,
        programStatic: Program<StaticDefaultUniforms>,
        programAnimated: Program<AnimatedDefaultUniforms>
    ): Unit = graphicsApi.run {
        profiled("Actual draw entities") {
            val mode = if (config.debug.isDrawLines) RenderingMode.Lines else RenderingMode.Fill

            val camera = renderState[primaryCameraStateHolder.camera]

            val drawDescriptionStatic = IndirectCulledDrawDescription(
                renderState,
                programStatic,
                commandOrganizationStatic,
                renderState[entitiesStateHolder.entitiesState].geometryBufferStatic,
                mode,
                camera,
                camera
            )
            val drawDescriptionAnimated = IndirectCulledDrawDescription(
                renderState,
                programAnimated,
                commandOrganizationAnimated,
                renderState[entitiesStateHolder.entitiesState].geometryBufferAnimated,
                mode,
                camera,
                camera
            )

            cullAndRender(drawDescriptionStatic, drawDescriptionAnimated)

//            redundant, because it's done above as long as i skip phase two of culling
//            drawDescriptionStatic.draw()
//            drawDescriptionAnimated.draw()
        }
    }

    private fun cullAndRender(
        drawDescriptionStatic: IndirectCulledDrawDescription<StaticDefaultUniforms>,
        drawDescriptionAnimated: IndirectCulledDrawDescription<AnimatedDefaultUniforms>
    ): Unit = graphicsApi.run {
        clearTexImage(
            highZBuffer.textures[0],
            Format.RGBA,
            0,
            TexelComponentType.UnsignedByte,
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
            Defines(), Uniforms.Empty
        )
    }

    private fun renderHighZMap() = graphicsApi.run {
        profile("HighZ map calculation") {
            highZProgram.use()
            var lastWidth = config.width
            var lastHeight = config.height
            var currentWidth = lastWidth / 2
            var currentHeight = lastHeight / 2
            val mipMapCount = calculateMipMapCount(currentWidth, currentHeight)
            for (mipmapTarget in 0 until mipMapCount) {
                highZProgram.setUniform("width", currentWidth)
                highZProgram.setUniform("height", currentHeight)
                highZProgram.setUniform("lastWidth", lastWidth)
                highZProgram.setUniform("lastHeight", lastHeight)
                highZProgram.setUniform("mipmapTarget", mipmapTarget)
                if (mipmapTarget == 0) {
                    bindTexture(0, TextureTarget.TEXTURE_2D, baseDepthTexture.id)
                } else {
                    bindTexture(0, TextureTarget.TEXTURE_2D, highZBuffer.renderedTexture)
                }
                bindImageTexture(
                    1,
                    highZBuffer.renderedTexture,
                    mipmapTarget,
                    false,
                    0,
                    Access.ReadWrite,
                    HIGHZ_FORMAT
                )
                bindTexture(2, TextureTarget.TEXTURE_2D, baseDepthTexture.id)
                val num_groups_x = max(1, (currentWidth + 7) / 8).toCount()
                val num_groups_y = max(1, (currentHeight + 7) / 8).toCount()
                highZProgram.dispatchCompute(num_groups_x, num_groups_y, 1.toCount())
                lastWidth = currentWidth
                lastHeight = currentHeight
                currentWidth /= 2
                currentHeight /= 2
                memoryBarrier(Barrier.ShaderImageAccess)
            }
        }
    }

    private fun determineVisibilities(
        renderState: RenderState,
        commandOrganization: CommandOrganizationGpuCulled,
        phase: CullingPhase, cullCam: Camera
    ) = graphicsApi.run {
        profiled("Visibility detection") {
            clearTexImage(debugMinMaxTexture, Format.RGBA, 0, TexelComponentType.Float)

            val occlusionCullingPhase =
                if (phase.coarsePhase == CoarseCullingPhase.ONE) occlusionCullingPhase1Vertex else occlusionCullingPhase2Vertex
            with(occlusionCullingPhase) {
                val invocationsPerCommand = 4096.toCount()
                use()
                with(commandOrganization) {
                    bindShaderStorageBuffer(1, instanceCountForCommand)
                    bindShaderStorageBuffer(3, renderState[entityBuffer.entitiesBuffer])
                    bindShaderStorageBuffer(4, offsetsForCommand)
                    bindShaderStorageBuffer(5, commands)
                    bindShaderStorageBuffer(9, visibilities)
                    bindShaderStorageBuffer(10, entitiesCompacted)
                    bindShaderStorageBuffer(11, entitiesCompactedCounter)
                    bindShaderStorageBuffer(12, commandOffsets)
                    bindShaderStorageBuffer(13, currentCompactedPointers)
                }
                setUniform("maxDrawCommands", commandOrganization.commandCount.value.toInt())
                val camera = cullCam
                setUniformAsMatrix4("viewProjectionMatrix", camera.viewProjectionMatrixBuffer)
                setUniformAsMatrix4("viewMatrix", camera.viewMatrixBuffer)
                setUniform("camPosition", camera.transform.position)
                setUniformAsMatrix4("projectionMatrix", camera.projectionMatrixBuffer)
                setUniform("useFrustumCulling", config.debug.isUseGpuFrustumCulling)
                setUniform("useOcclusionCulling", config.debug.isUseGpuOcclusionCulling)
                bindTexture(0, TextureTarget.TEXTURE_2D, highZBuffer.renderedTexture)
                //            bindImageTexture(1, highZBuffer.renderedTexture, 0, false, 0, GL15.GL_WRITE_ONLY, HIGHZ_FORMAT)
                bindImageTexture(
                    1,
                    debugMinMaxTexture.id,
                    0,
                    false,
                    0,
                    Access.WriteOnly,
                    debugMinMaxTexture.internalFormat
                )
                drawArraysInstanced(
                    PrimitiveType.Triangles,
                    0.toCount(),
                    ((commandOrganization.commandCount.value.toInt() + 2) / 3).toCount() * 3,
                    invocationsPerCommand
                )
                memoryBarrier(Barrier.ShaderImageAccess)
//                memoryBarrier()
            }
        }
    }

    private fun appendDrawCalls(
        commandOrganization: CommandOrganizationGpuCulled,
        renderState: RenderState
    ) = graphicsApi.run {
        val drawCountBuffer = commandOrganization.drawCountsCompacted
        drawCountBuffer.buffer.asIntBuffer().put(0, 0)
        val appendProgram = if (config.debug.isUseComputeShaderDrawCommandAppend) {
            appendDrawCommandsComputeProgram
        } else appendDrawCommandsProgram

        val entitiesState = renderState[entitiesStateHolder.entitiesState]
        profiled("Buffer compaction") {

            with(commandOrganization) {
                val instanceCount = commandOrganization.filteredRenderBatches.sumByLong {
                        it.drawElementsIndirectCommand.instanceCount.value
                    }.toCount()
                visibilities.ensureCapacityInBytes(instanceCount * SizeInBytes(IntStrukt.sizeInBytes))
                commandOrganization.entitiesCompacted.ensureCapacityInBytes(instanceCount * SizeInBytes(EntityStrukt.sizeInBytes))
                val entitiesCountersToUse = instanceCountForCommand
                entitiesCountersToUse.ensureCapacityInBytes(commandCount * SizeInBytes(IntStrukt.sizeInBytes))
                with(appendProgram) {
                    use()
                    bindShaderStorageBuffer(1, instanceCountForCommand)
                    bindShaderStorageBuffer(2, drawCountBuffer)
                    bindShaderStorageBuffer(3, renderState[entityBuffer.entitiesBuffer])
                    bindShaderStorageBuffer(4, offsetsForCommand)
                    bindShaderStorageBuffer(5, commands)
                    bindShaderStorageBuffer(7, commandsCompacted)
                    bindShaderStorageBuffer(8, offsetsCompacted)
                    bindShaderStorageBuffer(9, visibilities)
                    bindShaderStorageBuffer(12, commandOffsets)
                    bindShaderStorageBuffer(13, currentCompactedPointers)
                    setUniform("maxDrawCommands", commandCount.value.toInt())
                    bindImageTexture(
                        1,
                        debugMinMaxTexture.id,
                        0,
                        false,
                        0,
                        Access.WriteOnly,
                        debugMinMaxTexture.internalFormat
                    )
                    if (config.debug.isUseComputeShaderDrawCommandAppend) {
                        appendDrawCommandsComputeProgram.dispatchCompute(commandCount, 1.toCount(), 1.toCount())
                    } else {
                        val invocationsPerCommand = 4096
                        drawArraysInstanced(
                            PrimitiveType.Triangles,
                            0.toCount(),
                            ((invocationsPerCommand + 2) / 3).toCount() * 3,
                            commandCount
                        )
                    }
                    memoryBarrier(Barrier.ShaderImageAccess)
//                    memoryBarrier()
                }
            }
        }
    }

    private fun IndirectCulledDrawDescription<out DefaultUniforms>.cullAndRender(phase: CullingPhase) = graphicsApi.run {
        commandOrganization.drawCountsCompacted.buffer.asIntBuffer().put(0, 0)
        commandOrganization.entitiesCompactedCounter.buffer.asIntBuffer().put(0, 0)

        if (commandOrganization.commandCount != 0.toCount()) {
            profiled("Culling Phase") {
                determineVisibilities(renderState, commandOrganization, phase, cullCam)
                appendDrawCalls(commandOrganization, renderState)
            }
            val entitiesState = renderState[entitiesStateHolder.entitiesState]

            profiled("Actually render") {

                cullFace = useBackFaceCulling

                program.use()
                program.uniforms.apply {
                    materials = renderState[materialSystem.materialBuffer]
                    entities = renderState[entityBuffer.entitiesBuffer]
                    indirect = true
                    when (this) {
                        is StaticDefaultUniforms -> vertices = entitiesState.geometryBufferStatic.vertexStructArray
                        is AnimatedDefaultUniforms -> {
                            joints = entitiesState.jointsBuffer
                            vertices = entitiesState.geometryBufferAnimated.vertexStructArray
                        }
                    }
                    useRainEffect = config.effects.rainEffect != 0.0f
                    rainEffect = config.effects.rainEffect
                    viewMatrix = camera.viewMatrixBuffer
                    lastViewMatrix = camera.viewMatrixBuffer
                    projectionMatrix = camera.projectionMatrixBuffer
                    viewProjectionMatrix = camera.viewProjectionMatrixBuffer

                    eyePosition = camera.getPosition()
                    near = camera.near
                    far = camera.far
                    time = renderState.time.toInt()
                    useParallax = config.quality.isUseParallax
                    useSteepParallax = config.quality.isUseSteepParallax

                    entityIndex = 0
                    entityBaseIndex = 0
                    entities = renderState[entityBuffer.entitiesBuffer]
                    entityOffsets = commandOrganization.offsetsForCommand
                    when (this) {
                        is StaticDefaultUniforms -> Unit
                        is AnimatedDefaultUniforms -> joints = entitiesState.jointsBuffer
                    }
                }

                program.bind()
                when(geometryBuffer) {
                    is VertexBuffer -> drawArraysIndirectCount(
                        commandOrganization.commands,
                        commandOrganization.drawCountsCompacted,
                        0.toCount(),
                        commandOrganization.commandCount,
                        mode)
                    is VertexIndexBuffer -> geometryBuffer.drawElementsIndirectCount(
                        commandOrganization.commands,
                        commandOrganization.drawCountsCompacted,
                        0.toCount(),
                        commandOrganization.commandCount,
                        mode
                    )
                }
            }
        }
    }
}

val RenderBatch.canBeRenderedInIndirectBatch
    get() = !isForwardRendered && !hasOwnProgram && material.writesDepth && material.renderPriority == null


fun addCommands(
    renderBatches: List<RenderBatch>,
    commandBuffer: TypedGpuBuffer<DrawElementsIndirectCommandStrukt>,
    entityOffsetBuffer: TypedGpuBuffer<IntStrukt>
) {
    val resultingCommandCount = renderBatches.size.toCount()
    entityOffsetBuffer.ensureCapacityInBytes(resultingCommandCount * SizeInBytes(IntStrukt.sizeInBytes))
    entityOffsetBuffer.buffer.rewind()
    commandBuffer.ensureCapacityInBytes(resultingCommandCount * SizeInBytes(DrawElementsIndirectCommandStrukt.sizeInBytes))
    commandBuffer.byteBuffer.rewind()

    commandBuffer.typedBuffer.byteBuffer.run {
        for ((index, batch) in renderBatches.withIndex()) {
            val command = batch.drawElementsIndirectCommand
            commandBuffer.typedBuffer[index].run {
                count = command.count.value.toInt()
                instanceCount = command.instanceCount.value.toInt()
                firstIndex = command.firstIndex.value.toInt()
                baseVertex = command.baseVertex.value.toInt()
                baseInstance = command.baseInstance.value.toInt()
            }
            entityOffsetBuffer.typedBuffer.forIndex(index) { it.value = batch.entityBufferIndex }
        }
    }
    entityOffsetBuffer.buffer.rewind()
    commandBuffer.typedBuffer.byteBuffer.rewind()
}

private val HIGHZ_FORMAT = InternalTextureFormat.R32F

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

class CommandOrganizationGpuCulled(graphicsApi: GraphicsApi) {
    var commandCount = 0.toCount()
    var primitiveCount = 0
    var filteredRenderBatches: List<RenderBatch> = emptyList()
    val commands = CommandBuffer(graphicsApi, 10000.toCount())
    val commandsCompacted = CommandBuffer(graphicsApi, 10000.toCount())
    val offsetsForCommand = IndexBuffer(graphicsApi)

    val drawCountsCompacted = graphicsApi.AtomicCounterBuffer()
    val visibilities = IndexBuffer(graphicsApi)
    val commandOffsets = IndexBuffer(graphicsApi)
    val currentCompactedPointers = IndexBuffer(graphicsApi)
    val offsetsCompacted = IndexBuffer(graphicsApi)
    val entitiesCompacted = graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(EntityStrukt.type.sizeInBytes)).typed(EntityStrukt.type)
    val entitiesCompactedCounter = graphicsApi.AtomicCounterBuffer()
    val instanceCountForCommand = IndexBuffer(graphicsApi)
}

class IndirectCulledDrawDescription<T : DefaultUniforms>(
    val renderState: RenderState,
    val program: Program<T>,
    val commandOrganization: CommandOrganizationGpuCulled,
    val geometryBuffer: GeometryBuffer<*>,
    val mode: RenderingMode,
    val camera: Camera,
    val cullCam: Camera = camera,
)
