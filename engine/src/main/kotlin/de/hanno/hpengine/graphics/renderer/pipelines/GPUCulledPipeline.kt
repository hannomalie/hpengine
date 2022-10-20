package de.hanno.hpengine.graphics.renderer.pipelines

import EntityStruktImpl.Companion.sizeInBytes
import EntityStruktImpl.Companion.type
import de.hanno.hpengine.backend.OpenGl
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.AtomicCounterBuffer
import de.hanno.hpengine.graphics.renderer.IndirectCulledDrawDescription
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.graphics.renderer.drawstrategy.RenderingMode
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.ZERO_BUFFER
import de.hanno.hpengine.graphics.renderer.drawstrategy.renderHighZMap
import de.hanno.hpengine.graphics.renderer.pipelines.Pipeline.Companion.HIGHZ_FORMAT
import de.hanno.hpengine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.shader.useAndBind
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.model.texture.Texture2D
import de.hanno.hpengine.model.texture.Texture2D.TextureUploadInfo.Texture2DUploadInfo
import de.hanno.hpengine.model.texture.TextureDimension
import de.hanno.hpengine.model.texture.TextureManager
import de.hanno.hpengine.scene.VertexIndexBuffer
import de.hanno.hpengine.graphics.vertexbuffer.multiDrawElementsIndirectCount
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
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
) : Pipeline {
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

    override fun prepare(renderState: RenderState) {
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
                mode,
                renderState.camera,
                renderState.camera
            ) { renderState, program, renderCam ->
                beforeDrawStatic(renderState, program, renderCam)
            }
            val drawDescriptionAnimated = IndirectCulledDrawDescription(
                renderState,
                programAnimated,
                commandOrganizationAnimated,
                renderState.vertexIndexBufferAnimated,
                mode,
                renderState.camera,
                renderState.camera
            ) { renderState, program, renderCam ->
                beforeDrawAnimated(renderState, program, renderCam)
            }

            cullAndRender(drawDescriptionStatic, drawDescriptionAnimated)

//            redundant, because it's done above as long as i skip phase two of culling
//            drawDescriptionStatic.draw()
//            drawDescriptionAnimated.draw()

            firstPassResult.verticesDrawn += verticesCount
            firstPassResult.entitiesDrawn += entitiesCount
        }
    }

    override fun beforeDrawStatic(
        renderState: RenderState,
        program: Program<StaticFirstPassUniforms>,
        renderCam: Camera
    ) {
        beforeDraw(renderState, program, renderCam)
    }

    override fun beforeDrawAnimated(
        renderState: RenderState,
        program: Program<AnimatedFirstPassUniforms>,
        renderCam: Camera
    ) {
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
        val cullAndRenderHelper = { profilerString: String, phase: CoarseCullingPhase ->
            profiled(profilerString) {
                cullAndRender(drawDescriptionStatic, phase.staticPhase) {
                    beforeDrawStatic(
                        drawDescriptionStatic.renderState,
                        drawDescriptionStatic.program,
                        drawDescriptionStatic.drawCam
                    )
                }
                debugPrintPhase1(drawDescriptionStatic, CullingPhase.STATIC_ONE)

                cullAndRender(drawDescriptionAnimated, phase.animatedPhase) {
                    beforeDrawAnimated(
                        drawDescriptionAnimated.renderState,
                        drawDescriptionAnimated.program,
                        drawDescriptionAnimated.drawCam
                    )
                }
                debugPrintPhase1(drawDescriptionAnimated, CullingPhase.ANIMATED_ONE)

                renderHighZMap()
            }
        }
        cullAndRenderHelper("Cull&Render Phase1", CoarseCullingPhase.ONE)
    }

    private val highZProgram = config.run {
        programManager.getComputeProgram(
            EngineAsset("shaders/highZ_compute.glsl"),
            Defines()
        )
    }

    private fun renderHighZMap() = renderHighZMap(
        gpuContext,
        baseDepthTexture.id,
        config.width,
        config.height,
        highZBuffer.renderedTexture,
        highZProgram
    )

    private fun cullPhase(
        renderState: RenderState,
        commandOrganization: CommandOrganizationGpuCulled,
        phase: CullingPhase,
        cullCam: Camera
    ) = profiled("Culling Phase") {
        determineVisibilities(renderState, commandOrganization, phase, cullCam)
        appendDrawCalls(commandOrganization, renderState)
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
            gpuContext.bindTexture(
                0,
                de.hanno.hpengine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D,
                highZBuffer.renderedTexture
            )
//            gpuContext.bindImageTexture(1, highZBuffer.renderedTexture, 0, false, 0, GL15.GL_WRITE_ONLY, HIGHZ_FORMAT)
            gpuContext.bindImageTexture(
                1,
                debugMinMaxTexture.id,
                0,
                false,
                0,
                GL15.GL_WRITE_ONLY,
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
        val appendProgram =
            if (config.debug.isUseComputeShaderDrawCommandAppend) appendDrawCommandsComputeProgram else appendDrawCommandsProgram

        profiled("Buffer compaction") {

            with(commandOrganization) {
                val instanceCount =
                    commandOrganization.filteredRenderBatches.sumByLong { it.drawElementsIndirectCommand.instanceCount.toLong() }
                        .toInt()
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

    private fun cullAndRender(
        drawDescription: IndirectCulledDrawDescription<out FirstPassUniforms>,
        phase: CullingPhase,
        beforeRender: () -> Unit
    ) = with(drawDescription.commandOrganization) {
        drawCountsCompacted.put(0, 0)
        entitiesCompactedCounter.put(0, 0)
        if (drawDescription.commandOrganization.commandCount != 0) {
            with(drawDescription) {
                cullPhase(renderState, commandOrganization, phase, cullCam)
//                render(renderState, program, commandOrganization, vertexIndexBuffer, drawDescription.mode, beforeRender)
                drawDescription.draw()
            }
        }
    }

    private fun render(
        renderState: RenderState,
        program: Program<out FirstPassUniforms>,
        commandOrganization: CommandOrganizationGpuCulled,
        vertexIndexBuffer: VertexIndexBuffer,
        mode: RenderingMode,
        beforeRender: () -> Unit
    ) = profiled("Actually render") {
        program.use()
        program.setUniforms(renderState, renderState.camera, config, true)
        beforeRender()
        program.setUniform("entityIndex", 0)
        program.setUniform("entityBaseIndex", 0)
        program.setUniform("indirect", true)
        program.bindShaderStorageBuffer(3, commandOrganization.entitiesCompacted)
        program.bindShaderStorageBuffer(4, commandOrganization.offsetsCompacted)
        program.bindShaderStorageBuffer(6, renderState.entitiesState.jointsBuffer)
        vertexIndexBuffer.multiDrawElementsIndirectCount(
            commandOrganization.commandsCompacted,
            commandOrganization.drawCountsCompacted,
            0,
            commandOrganization.filteredRenderBatches.size,
            mode
        )
    }

    private fun debugPrintPhase1(
        drawDescription: IndirectCulledDrawDescription<out FirstPassUniforms>,
        phase: CullingPhase
    ) {
        if (config.debug.isPrintPipelineDebugOutput) {
            GL11.glFinish()

            fun printPhase1(commandOrganization: CommandOrganizationGpuCulled) {
                with(commandOrganization) {
                    val commandCount = drawCountsCompacted.buffer.getInt(0)
                    if (commandCount == 0) return

                    val instanceCount = entitiesCompactedCounter.buffer.getInt(0)
                    println("########### $phase ")
                    println("Visibilities")
                    val visibilityCount = if (commandCount == 0) {
                        0
                    } else {
                        var result = 0
                        commandsCompacted.typedBuffer.forEach(commandCount) { result += it.instanceCount }
                        result
                    }
                    Util.printIntBuffer(visibilities.buffer.asIntBuffer(), visibilityCount, 1)
                    println("Instance counts per command")
                    Util.printIntBuffer(instanceCountForCommand.buffer.asIntBuffer(), commandCount, 1)
                    println("DrawCounts compacted")
                    println(drawCountsCompacted.buffer.getInt(0))
                    println("Number of visible instances")
                    println(instanceCount)
                    println("Current compacted Pointers")
                    Util.printIntBuffer(currentCompactedPointers.buffer.asIntBuffer(), commandCount, 1)
                    println("Command offsets")
                    Util.printIntBuffer(commandOffsets.buffer.asIntBuffer(), commandCount, 1)
                    println("Command offsets culled")
                    Util.printIntBuffer(offsetsCompacted.buffer.asIntBuffer(), commandCount, 1)
                    println("Commands input")
                    Util.printIntBuffer(commands.buffer.asIntBuffer(), 5, commandCount)
                    println("Commands compacted")
                    Util.printIntBuffer(commandsCompacted.buffer.asIntBuffer(), 5, commandCount)
                    println("Entities compacted")
                    entitiesCompacted.typedBuffer.forEach(min(instanceCount, 10)) { println(it.print()) }
                }
            }

            printPhase1(drawDescription.commandOrganization)
        }
    }
}
val RenderBatch.canBeRenderedInIndirectBatch
    get() = !isForwardRendered && !hasOwnProgram && material.writesDepth && material.renderPriority == null

fun <T : FirstPassUniforms> IndirectCulledDrawDescription<T>.draw() {
    beforeDraw(renderState, program, drawCam)
    with(commandOrganization) {
        profiled("Actually render") {
            program.useAndBind { uniforms ->
                uniforms.entityIndex = 0
                uniforms.entityBaseIndex = 0
                uniforms.entities = renderState.entitiesState.entitiesBuffer
                uniforms.entityOffsets = offsetsForCommand
                when (uniforms) {
                    is StaticFirstPassUniforms -> Unit
                    is AnimatedFirstPassUniforms -> uniforms.joints = renderState.entitiesState.jointsBuffer
                    else -> throw IllegalStateException("This can never happen")
                }

                vertexIndexBuffer.multiDrawElementsIndirectCount(commands, drawCountsCompacted, 0, commandCount, mode)
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