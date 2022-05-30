package de.hanno.hpengine.engine.graphics.renderer.pipelines

import EntityStruktImpl.Companion.sizeInBytes
import EntityStruktImpl.Companion.type
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.EntityStrukt
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.AtomicCounterBuffer
import de.hanno.hpengine.engine.graphics.renderer.IndirectCulledDrawDescription
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.RenderingMode
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.VoxelConeTracingExtension
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.ZERO_BUFFER
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.renderHighZMap
import de.hanno.hpengine.engine.graphics.renderer.pipelines.Pipeline.Companion.HIGHZ_FORMAT
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.Uniforms
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.shader.useAndBind
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.engine.model.texture.Texture2D.TextureUploadInfo.Texture2DUploadInfo
import de.hanno.hpengine.engine.model.texture.TextureDimension
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.engine.vertexbuffer.multiDrawElementsIndirectCount
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL15.GL_WRITE_ONLY
import kotlin.math.min

open class GPUCulledPipeline @JvmOverloads constructor(
    private val config: Config,
    private val gpuContext: GpuContext<OpenGl>,
    private val programManager: ProgramManager<OpenGl>,
    private val textureManager: TextureManager,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val useBackFaceCulling: Boolean = true,
): Pipeline {
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
        frameBuffer = FrameBuffer(
            gpuContext,
            null
//            DepthBuffer(
//                gpuContext,
//                config.width/2,
//                config.height/2
//            )
        ),
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
    override fun prepare(renderState: RenderState) {
        if(config.debug.freezeCulling) return
        verticesCount = 0
        entitiesCount = 0

        fun CommandOrganizationGpuCulled.prepare(batches: List<RenderBatch>) {
            filteredRenderBatches = batches.filterNot {
                if(config.debug.isUseCpuFrustumCulling) {
                    it.isCulledOrForwardRendered(renderState.camera)
                } else {
                    it.isForwardRendered()
                }
            }.filterNot { it.hasOwnProgram }
            commandCount = filteredRenderBatches.size
            addCommands(filteredRenderBatches, commandBuffer, entityOffsetBuffer)
        }

        commandOrganizationStatic.prepare(renderState.renderBatchesStatic)
        commandOrganizationAnimated.prepare(renderState.renderBatchesAnimated)
    }


    override fun draw(
        renderState: RenderState,
        programStatic: Program<StaticFirstPassUniforms>,
        programAnimated: Program<AnimatedFirstPassUniforms>,
        firstPassResult: FirstPassResult
    ) {
        profiled("Actual draw entities") {
            val mode = if (config.debug.isDrawLines) RenderingMode.Lines else RenderingMode.Faces

            val drawDescriptionStatic = IndirectCulledDrawDescription(
                renderState,
                programStatic,
                commandOrganizationStatic,
                renderState.vertexIndexBufferStatic,
                this::beforeDrawStatic,
                mode,
                renderState.camera
            )
            val drawDescriptionAnimated = IndirectCulledDrawDescription(
                renderState,
                programAnimated,
                commandOrganizationAnimated,
                renderState.vertexIndexBufferAnimated,
                this::beforeDrawAnimated,
                mode,
                renderState.camera
            )

            beforeDraw(drawDescriptionStatic, drawDescriptionAnimated)

            drawDescriptionStatic.draw()
            drawDescriptionAnimated.draw()

            firstPassResult.verticesDrawn += verticesCount
            firstPassResult.entitiesDrawn += entitiesCount
        }
    }

    override fun beforeDrawStatic(renderState: RenderState, program: Program<StaticFirstPassUniforms>, renderCam: Camera) {
        beforeDraw(renderState, program, renderCam)
    }

    override fun beforeDrawAnimated(renderState: RenderState, program: Program<AnimatedFirstPassUniforms>, renderCam: Camera) {
        beforeDraw(renderState, program, renderCam)
    }

    fun beforeDraw(
        renderState: RenderState, program: Program<out FirstPassUniforms>,
        renderCam: Camera
    ) {
        gpuContext.cullFace = useBackFaceCulling
        program.use()
        program.setUniforms(renderState, renderCam, config, true)
    }

    private fun beforeDraw(
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
        val cullAndRender = { profilerString: String, phase: CoarseCullingPhase -> profiled(profilerString) {
                cullAndRender(drawDescriptionStatic, phase.staticPhase) {
                    beforeDrawStatic(drawDescriptionStatic.renderState, drawDescriptionStatic.program, drawDescriptionStatic.drawCam)
                }
                debugPrintPhase1(drawDescriptionStatic, CullingPhase.STATIC_ONE)

                cullAndRender(drawDescriptionAnimated, phase.animatedPhase) {
                    beforeDrawAnimated(drawDescriptionAnimated.renderState, drawDescriptionAnimated.program, drawDescriptionAnimated.drawCam)
                }
                debugPrintPhase1(drawDescriptionAnimated, CullingPhase.ANIMATED_ONE)

                renderHighZMap()
            }
        }
        cullAndRender("Cull&Render Phase1", CoarseCullingPhase.ONE)
    }

    private val highZProgram = config.run {
        programManager.getComputeProgram(
            EngineAsset("shaders/highZ_compute.glsl"),
            Defines()
//            Defines(Define.getDefine("SOURCE_CHANNEL_R", true))
        )
    }

    private fun renderHighZMap() = renderHighZMap(gpuContext, baseDepthTexture.id, config.width, config.height, highZBuffer.renderedTexture, highZProgram)

    private fun debugPrintPhase1(drawDescription: IndirectCulledDrawDescription<out FirstPassUniforms>, phase: CullingPhase) {
        if (config.debug.isPrintPipelineDebugOutput) {
            GL11.glFinish()

            fun printPhase1(commandOrganization: CommandOrganizationGpuCulled) {
                with(commandOrganization) {
                    val commandCount = drawCountsCompacted.buffer.getInt(0)
                    if(commandCount == 0) return

                    println("########### $phase ")
                    println("Visibilities")
                    val visibilityCount = if(commandCount == 0) {
                        0
                    } else {
                        var result = 0
                        commandBuffer.typedBuffer.forEach(commandCount) { result += it.instanceCount }
                        result
                    }
                    Util.printIntBuffer(visibilityBuffers.buffer.asIntBuffer(), visibilityCount, 1)
                    println("Entity instance counts")
                    Util.printIntBuffer(entitiesCounters.buffer.asIntBuffer(), commandCount, 1)
                    println("DrawCountsCompactedBuffer")
                    println(drawCountsCompacted.buffer.getInt(0))
                    println("Entities compacted counter")
                    println(entitiesCompactedCounter.buffer.getInt(0))
                    println("Offsets culled")
                    Util.printIntBuffer(entityOffsetBuffersCulled.buffer.asIntBuffer(), entitiesCompactedCounter.buffer.getInt(0), 1)
                    println("CurrentCompactedPointers")
                    Util.printIntBuffer(currentCompactedPointers.buffer.asIntBuffer(), entitiesCompactedCounter.buffer.getInt(0), 1)
                    println("CommandOffsets")
                    Util.printIntBuffer(commandOffsets.buffer.asIntBuffer(), entitiesCompactedCounter.buffer.getInt(0), 1)
                    if(commandCount > 0) {
                        println("Commands")
                        Util.printIntBuffer(commandBuffer.buffer.asIntBuffer(), 5, commandCount)
                        println("Entities compacted")
                        entitiesBuffersCompacted.typedBuffer.forEach(min(entitiesCompactedCounter.buffer.getInt(0), 10)) { println(it.print()) }
                    }
                }
            }

            printPhase1(drawDescription.commandOrganization)
        }
    }

    private fun cullPhase(
        renderState: RenderState,
        commandOrganization: CommandOrganizationGpuCulled,
        drawCountBuffer: AtomicCounterBuffer,
        targetCommandBuffer: PersistentTypedBuffer<DrawElementsIndirectCommandStrukt>,
        phase: CullingPhase, cullCam: Camera
    ) = profiled("Culling Phase") {
        cull(renderState, commandOrganization, phase, cullCam)

        drawCountBuffer.put(0, 0)
        val appendProgram =
            if (config.debug.isUseComputeShaderDrawCommandAppend) appendDrawCommandsComputeProgram else appendDrawCommandsProgram

        profiled("Buffer compaction") {

            with(commandOrganization) {
                val instanceCount = commandOrganization.filteredRenderBatches.sumByLong { it.drawElementsIndirectCommand.instanceCount.toLong() }.toInt()
                visibilityBuffers.resize(instanceCount)
                commandOrganization.entitiesBuffersCompacted.ensureCapacityInBytes(instanceCount * EntityStrukt.sizeInBytes)
                val entitiesCountersToUse = entitiesCounters
                entitiesCountersToUse.resize(commandCount)
                with(appendProgram) {
                    use()
                    bindShaderStorageBuffer(1, entitiesCounters)
                    bindShaderStorageBuffer(2, drawCountBuffer)
                    bindShaderStorageBuffer(3, renderState.entitiesState.entitiesBuffer)
                    bindShaderStorageBuffer(4, entityOffsetBuffer)
                    bindShaderStorageBuffer(5, commandBuffer)
                    bindShaderStorageBuffer(7, targetCommandBuffer)
                    bindShaderStorageBuffer(8, entityOffsetBuffersCulled)
                    bindShaderStorageBuffer(9, visibilityBuffers)
                    bindShaderStorageBuffer(10, entitiesBuffersCompacted)
                    bindShaderStorageBuffer(11, entitiesCompactedCounter)
                    bindShaderStorageBuffer(12, commandOffsets)
                    bindShaderStorageBuffer(13, currentCompactedPointers)
                    setUniform("maxDrawCommands", commandCount)
                    gpuContext.bindImageTexture(1, debugMinMaxTexture.id, 0, false, 0, GL_WRITE_ONLY, debugMinMaxTexture.internalFormat)
                    if (config.debug.isUseComputeShaderDrawCommandAppend) {
                        appendDrawCommandsComputeProgram.dispatchCompute(commandCount, 1, 1)
                    } else {
                        val invocationsPerCommand: Int = 4096//commands.map { it.primCount }.max()!!//4096
                        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, (invocationsPerCommand + 2) / 3 * 3, commandCount)
                    }
                    GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT or GL42.GL_TEXTURE_FETCH_BARRIER_BIT or GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT or GL42.GL_COMMAND_BARRIER_BIT)
                    GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
                }
            }
        }
    }

    private fun cullAndRender(
        drawDescription: IndirectCulledDrawDescription<out FirstPassUniforms>,
        phase: CullingPhase,
        beforeRender: () -> Unit
    ) = with(drawDescription.commandOrganization) {
        drawCountsCompacted.put(0, 0)
        entitiesCompactedCounter.put(0, 0)
        if (drawDescription.commandOrganization.commandCount != 0) {
            with(drawDescription) {
                cullPhase(renderState, commandOrganization, drawCountsCompacted, commandBuffer, phase, cullCam)
                render(
                    renderState,
                    program,
                    commandOrganization,
                    vertexIndexBuffer,
                    drawCountsCompacted,
                    commandBuffer,
                    entityOffsetBuffersCulled,
                    beforeRender,
                    drawDescription.mode
                )
            }
        }
    }

    private fun render(
        renderState: RenderState,
        program: Program<out FirstPassUniforms>,
        commandOrganization: CommandOrganizationGpuCulled,
        vertexIndexBuffer: VertexIndexBuffer,
        drawCountBuffer: AtomicCounterBuffer,
        commandBuffer: PersistentTypedBuffer<DrawElementsIndirectCommandStrukt>,
        offsetBuffer: PersistentMappedStructBuffer<IntStruct>,
        beforeRender: () -> Unit,
        mode: RenderingMode
    ) = profiled("Actually render") {
        program.use()
        program.setUniforms(renderState, renderState.camera, config, true)
        beforeRender()
        program.setUniform("entityIndex", 0)
        program.setUniform("entityBaseIndex", 0)
        program.setUniform("indirect", true)
        if (config.debug.isUseGpuOcclusionCulling) {
            program.bindShaderStorageBuffer(3, commandOrganization.entitiesBuffersCompacted)
            program.bindShaderStorageBuffer(4, commandOrganization.entityOffsetBuffersCulled)
        } else {
            program.bindShaderStorageBuffer(3, renderState.entitiesState.entitiesBuffer)
            program.bindShaderStorageBuffer(4, offsetBuffer)
        }
//        Check out why this is necessary to be bound
        program.bindShaderStorageBuffer(3, commandOrganization.entitiesBuffersCompacted)
        program.bindShaderStorageBuffer(4, commandOrganization.entityOffsetBuffersCulled)
        program.bindShaderStorageBuffer(6, renderState.entitiesState.jointsBuffer)
        vertexIndexBuffer.multiDrawElementsIndirectCount(
            commandBuffer,
            drawCountBuffer,
            0,
            commandOrganization.commandCount,
            mode
        )
    }

    private fun cull(
        renderState: RenderState,
        commandOrganization: CommandOrganizationGpuCulled,
        phase: CullingPhase, cullCam: Camera
    ) = profiled("Visibility detection") {
        ARBClearTexture.glClearTexImage(debugMinMaxTexture.id, 0, GL11.GL_RGBA, GL11.GL_FLOAT, ZERO_BUFFER)

        val occlusionCullingPhase =
            if (phase.coarsePhase == CoarseCullingPhase.ONE) occlusionCullingPhase1Vertex else occlusionCullingPhase2Vertex
        with(occlusionCullingPhase) {
//            commandOrganization.commands.map { it.primCount }.reduce({ a, b -> a + b })
            val invocationsPerCommand: Int = 4096 // commandOrganization.commands.map { it.primCount }.max()!!
            use()
            with(commandOrganization) {
                bindShaderStorageBuffer(1, entitiesCounters)
                bindShaderStorageBuffer(3, renderState.entitiesState.entitiesBuffer)
                bindShaderStorageBuffer(4, entityOffsetBuffer)
                bindShaderStorageBuffer(5, commandBuffer)
                bindShaderStorageBuffer(8, entityOffsetBuffersCulled)
                bindShaderStorageBuffer(9, visibilityBuffers)
                bindShaderStorageBuffer(10, entitiesBuffersCompacted)
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
            gpuContext.bindImageTexture(1, debugMinMaxTexture.id, 0, false, 0, GL15.GL_WRITE_ONLY, debugMinMaxTexture.internalFormat)
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, (commandOrganization.commandCount + 2) / 3 * 3, invocationsPerCommand)
            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT)
            GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
        }
    }
}

fun <T: FirstPassUniforms> IndirectCulledDrawDescription<T>.draw() {
    beforeDraw(renderState, program, drawCam)
    with(commandOrganization) {
        profiled("Actually render") {
            program.useAndBind { uniforms ->
                uniforms.entityIndex = 0
                uniforms.entityBaseIndex = 0
                uniforms.entities = renderState.entitiesState.entitiesBuffer
                uniforms.entityOffsets = entityOffsetBuffer
                when(uniforms) {
                    is StaticFirstPassUniforms -> Unit
                    is AnimatedFirstPassUniforms -> uniforms.joints = renderState.entitiesState.jointsBuffer
                    else -> throw IllegalStateException("This can never happen")
                }

                vertexIndexBuffer.multiDrawElementsIndirectCount(commandBuffer, drawCountsCompacted, 0, commandCount, mode)
            }
        }
    }
}

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
    get() = if(this == CoarseCullingPhase.ONE) CullingPhase.STATIC_ONE else CullingPhase.STATIC_TWO
val CoarseCullingPhase.animatedPhase: CullingPhase
    get() = if(this == CoarseCullingPhase.ONE) CullingPhase.ANIMATED_ONE else CullingPhase.ANIMATED_TWO

class CommandOrganizationGpuCulled(gpuContext: GpuContext<*>) {
    var commandCount = 0
    var primitiveCount = 0
    var filteredRenderBatches: List<RenderBatch> = emptyList()
    val commandBuffer = CommandBuffer(gpuContext, 10000)

    val entityOffsetBuffer = IndexBuffer(gpuContext, 10000)

    val drawCountsCompacted = AtomicCounterBuffer(gpuContext, 1)
    val visibilityBuffers = IndexBuffer(gpuContext, 10000)
    val commandOffsets = IndexBuffer(gpuContext, 10000)
    val currentCompactedPointers = IndexBuffer(gpuContext, 10000)
    val entityOffsetBuffersCulled = IndexBuffer(gpuContext, 10000)
    val entitiesBuffersCompacted = PersistentMappedBuffer(EntityStrukt.type.sizeInBytes, gpuContext).typed(EntityStrukt.type)
    val entitiesCompactedCounter = AtomicCounterBuffer(gpuContext, 1)
    val entitiesCounters = IndexBuffer(gpuContext)
}