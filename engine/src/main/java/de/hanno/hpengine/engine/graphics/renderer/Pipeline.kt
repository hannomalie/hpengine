package de.hanno.hpengine.engine.graphics.renderer

import com.carrotsearch.hppc.IntArrayList
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.renderer.Pipeline.CoarseCullingPhase.*
import de.hanno.hpengine.engine.graphics.renderer.Pipeline.CullingPhase.*
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SimpleDrawStrategy.renderHighZMap
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.VoxelConeTracingExtension.ZERO_BUFFER
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetBuilder
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramFactory
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.CommandBuffer
import de.hanno.hpengine.engine.model.Entity
import de.hanno.hpengine.engine.model.IndexBuffer
import de.hanno.hpengine.engine.model.VertexBuffer.*
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.util.Util.*
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.glFinish
import org.lwjgl.opengl.GL42.*
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT

open class Pipeline @JvmOverloads constructor(private val useFrustumCulling: Boolean = true, private val useBackfaceCulling: Boolean = true, private val useLineDrawingIfActivated: Boolean = true) {

    private val highZBuffer: RenderTarget by lazy {
        RenderTargetBuilder()
                .setWidth(Config.getInstance().width / 2).setHeight(Config.getInstance().height / 2)
                .add(ColorAttachmentDefinition().setInternalFormat(HIGHZ_FORMAT)
                        .setTextureFilter(GL11.GL_NEAREST_MIPMAP_NEAREST))
                .build()
    }
    private lateinit var occlusionCullingPhase1Vertex: Program
    private lateinit var occlusionCullingPhase2Vertex: Program

    init {
        try {
            this.occlusionCullingPhase1Vertex = ProgramFactory.getInstance().getProgram("occlusion_culling1_vertex.glsl", null)
            this.occlusionCullingPhase2Vertex = ProgramFactory.getInstance().getProgram("occlusion_culling2_vertex.glsl", null)
        } catch (e: Exception) {
            e.printStackTrace()
            System.exit(-1)
        }

    }

    fun prepareAndDraw(renderState: RenderState, programStatic: Program, programAnimated: Program, firstPassResult: FirstPassResult) {
        prepare(renderState)
        draw(renderState, programStatic, programAnimated, firstPassResult)
    }


    fun draw(renderState: RenderState, programStatic: Program, programAnimated: Program, firstPassResult: FirstPassResult) {
        GPUProfiler.start("Actual draw entities")
        with(renderState) {
            drawStaticAndAnimated(DrawDescription(renderState, programStatic, commandOrganizationStatic, renderState.vertexIndexBufferStatic),
                    DrawDescription(renderState, programAnimated, commandOrganizationAnimated, renderState.vertexIndexBufferAnimated))

            firstPassResult.verticesDrawn += verticesCount
            firstPassResult.entitiesDrawn += entitiesDrawn
        }
        GPUProfiler.end()
    }

    protected open fun drawStaticAndAnimated(drawDescriptionStatic: DrawDescription, drawDescriptionAnimated: DrawDescription) {
        if (Config.getInstance().isUseOcclusionCulling) {

            ARBClearTexture.glClearTexImage(highZBuffer.renderedTexture, 0, GL_RGBA, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER)

            val cullAndRender = { profilerString: String,
                                  phase: CoarseCullingPhase ->
                GPUProfiler.start(profilerString)
                cullAndRender(drawDescriptionStatic, { beforeDrawStatic(drawDescriptionStatic.renderState, drawDescriptionStatic.program) }, phase.staticPhase)
                cullAndRender(drawDescriptionAnimated, { beforeDrawAnimated(drawDescriptionAnimated.renderState, drawDescriptionAnimated.program) }, phase.animatedPhase)
                renderHighZMap(Renderer.getInstance().gBuffer.visibilityMap, Config.getInstance().width, Config.getInstance().height, highZBuffer.renderedTexture, ProgramFactory.getInstance().highZProgram)
                GPUProfiler.end()
            }

            cullAndRender("Cull&Render Phase1", ONE)
            debugPrintPhase1(drawDescriptionStatic, CullingPhase.STATIC_ONE)
            debugPrintPhase1(drawDescriptionAnimated, CullingPhase.ANIMATED_ONE)
//            cullAndRender("Cull&Render Phase2", TWO)
        } else {
            val drawCountBuffer = drawDescriptionStatic.commandOrganization.drawCountBuffer
            with(drawDescriptionStatic) {
                with(drawDescriptionStatic.commandOrganization) {
                    drawCountBuffer.put(0, commands.size)
                    render(renderState, program, this@with, vertexIndexBuffer, drawCountBuffer, commandBuffer, entityOffsetBuffer, {
                        beforeDrawStatic(renderState, program)
                    }, STATIC_ONE)
                }
            }
            with(drawDescriptionAnimated) {
                with(drawDescriptionAnimated.commandOrganization) {
                    drawCountBuffer.put(0, commands.size)
                    render(renderState, program, this@with, vertexIndexBuffer, drawCountBuffer, commandBuffer, entityOffsetBuffer, {
                        beforeDrawAnimated(renderState, program)
                    }, ANIMATED_ONE)
                }
            }
        }
    }

    private fun debugPrintPhase1(drawDescription: DrawDescription, phase: CullingPhase) {
        if (Config.getInstance().isPrintPipelineDebugOutput) {
            glFinish()
            println("########### $phase ")

            fun printPhase1(commandOrganization: CommandOrganization) {
                with(commandOrganization) {
                    println("Visibilities")
                    printIntBuffer(visibilityBuffers[phase]!!.buffer.asIntBuffer(), if (commands.isEmpty()) 0 else commands.map { it.primCount }.reduce({ a, b -> a + b }), 1)
                    println("Entity instance counts")
                    printIntBuffer(entitiesCounters[phase]!!.buffer.asIntBuffer(), commands.size, 1)
                    println("DrawCountBuffer")
                    println(drawCountBuffer.buffer.asIntBuffer().get(0))
                    println("Entities compacted counter")
                    println(entitiesCompactedCounter[phase]!!.buffer.asIntBuffer().get(0))
                    println("Offsets culled")
                    printIntBuffer(entityOffsetBuffersCulled[phase]!!.buffer.asIntBuffer(), commands.size, 1)
                    println("CurrentCompactedPointers")
                    printIntBuffer(currentCompactedPointers[phase]!!.buffer.asIntBuffer(), commands.size, 1)
                    println("CommandOffsets")
                    printIntBuffer(commandOffsets[phase]!!.buffer.asIntBuffer(), commands.size, 1)
                    if(!commands.isEmpty()) {
                        println("Commands")
                        printIntBuffer(commandBuffers[phase]!!.buffer.asIntBuffer(), 5, drawCountBuffers[phase]!!.buffer.asIntBuffer().get(0))
                        println("Entities compacted")
                        printFloatBuffer(entitiesBuffersCompacted[phase]!!.buffer.asFloatBuffer(), 40, 10)
                    }
                }
            }

            printPhase1(drawDescription.commandOrganization)
        }
    }

    private fun cullAndRender(drawDescription: DrawDescription, beforeRender: () -> Unit, phase: CullingPhase) {
        with(drawDescription.commandOrganization) {
            val drawCountBuffer = drawCountBuffers[phase]!!
            drawCountBuffer.put(0, 0)
            val entitiesCompactedCounter1 = entitiesCompactedCounter[phase]!!
            entitiesCompactedCounter1.put(0,0)
            if (commands.isEmpty()) {
                return
            }
            val targetCommandBuffer = commandBuffers[phase]!!

            with(drawDescription) {
                cullPhase(renderState, commandOrganization, drawCountBuffer, targetCommandBuffer, phase)
                render(renderState, program, commandOrganization, vertexIndexBuffer, drawCountBuffer, targetCommandBuffer, entityOffsetBuffersCulled[phase]!!, beforeRender, phase)
            }
        }
    }

    private fun cullPhase(renderState: RenderState, commandOrganization: CommandOrganization, drawCountBuffer: AtomicCounterBuffer, targetCommandBuffer: CommandBuffer, phase: CullingPhase) {
        GPUProfiler.start("Culling Phase")
        cull(renderState, commandOrganization, phase)

        drawCountBuffer.put(0, 0)
        val appendProgram = ProgramFactory.getInstance().appendDrawCommandProgram

        GPUProfiler.start("Buffer compaction")
        with(commandOrganization) {
            commandBuffers[phase]!!.sizeInBytes = commandBuffer.sizeInBytes
            val instanceCount = commands.map { it.primCount }.reduce({ a, b -> a + b })
            visibilityBuffers[phase]!!.sizeInBytes = instanceCount * java.lang.Integer.BYTES
            commandOrganization.entitiesBuffersCompacted[phase]!!.sizeInBytes = instanceCount * Entity.getBytesPerInstance()
            val entitiesCountersToUse = entitiesCounters[phase]!!
            entitiesCountersToUse.sizeInBytes = commands.size * java.lang.Integer.BYTES
            with(appendProgram) {
                val invocationsPerCommand : Int = commands.map { it.primCount }.max()!!//4096
                use()
                bindShaderStorageBuffer(1, entitiesCounters[phase]!!)
                bindShaderStorageBuffer(2, drawCountBuffer)
                bindShaderStorageBuffer(3, renderState.entitiesState.entitiesBuffer)
                bindShaderStorageBuffer(4, entityOffsetBuffer)
                bindShaderStorageBuffer(5, commandBuffer)
                bindShaderStorageBuffer(7, targetCommandBuffer)
                bindShaderStorageBuffer(8, entityOffsetBuffersCulled[phase]!!)
                bindShaderStorageBuffer(9, visibilityBuffers[phase]!!)
                bindShaderStorageBuffer(10, entitiesBuffersCompacted[phase]!!)
                bindShaderStorageBuffer(11, entitiesCompactedCounter[phase]!!)
                bindShaderStorageBuffer(12, commandOffsets[phase]!!)
                bindShaderStorageBuffer(13, currentCompactedPointers[phase]!!)
                setUniform("maxDrawCommands", commands.size)
                GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, (invocationsPerCommand + 2) / 3 * 3, commands.size)
                glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT or GL_TEXTURE_FETCH_BARRIER_BIT or GL_SHADER_IMAGE_ACCESS_BARRIER_BIT or GL_COMMAND_BARRIER_BIT)
                glMemoryBarrier(GL_ALL_BARRIER_BITS)
            }
        }
        GPUProfiler.end()
        GPUProfiler.end()
    }

    private fun render(renderState: RenderState, program: Program, commandOrganization: CommandOrganization, vertexIndexBuffer: VertexIndexBuffer<*>, drawCountBuffer: AtomicCounterBuffer, commandBuffer: CommandBuffer, offsetBuffer: IndexBuffer, beforeRender: () -> Unit, phase: CullingPhase) {
        GPUProfiler.start("Actually render")
        program.use()
        beforeRender()
        if (Config.getInstance().isIndirectRendering) {
            program.setUniform("entityIndex", 0)
            program.setUniform("entityBaseIndex", 0)
            program.setUniform("indirect", true)
            var drawCountBufferToUse = if(Config.getInstance().isUseOcclusionCulling) {
                program.bindShaderStorageBuffer(3, commandOrganization.entitiesBuffersCompacted[phase]!!)
                program.bindShaderStorageBuffer(4, commandOrganization.entityOffsetBuffersCulled[phase]!!)
                drawCountBuffer
            } else {
                program.bindShaderStorageBuffer(3, renderState.entitiesState.entitiesBuffer)
                program.bindShaderStorageBuffer(4, offsetBuffer)
                drawCountBuffer
            }
            program.bindShaderStorageBuffer(6, renderState.entitiesState.jointsBuffer)
            drawIndirect(vertexIndexBuffer, commandBuffer, commandOrganization.commands.size, drawCountBufferToUse)
        } else {
            for (i in commandOrganization.commands.indices) {
                val command = commandOrganization.commands[i]
                program.setUniform("entityIndex", commandOrganization.offsets.get(i))
                program.setUniform("entityBaseIndex", 0)
                program.setUniform("indirect", false)
                vertexIndexBuffer.vertexBuffer
                        .drawInstancedBaseVertex(vertexIndexBuffer.indexBuffer, command.count, command.primCount, command.firstIndex, command.baseVertex)
            }
        }
        GPUProfiler.end()
    }

    private fun cull(renderState: RenderState, commandOrganization: CommandOrganization, phase: CullingPhase) {
        GPUProfiler.start("Visibility detection")
        val occlusionCullingPhase = if (phase.coarsePhase == ONE) occlusionCullingPhase1Vertex else occlusionCullingPhase2Vertex
        with(occlusionCullingPhase) {
            commandOrganization.commands.map { it.primCount }.reduce({ a, b -> a + b })
            val invocationsPerCommand : Int = commandOrganization.commands.map { it.primCount }.max()!!//4096
            use()
            with(commandOrganization) {
                bindShaderStorageBuffer(1, entitiesCounters[phase]!!)
                bindShaderStorageBuffer(2, drawCountBuffer)
                bindShaderStorageBuffer(3, renderState.entitiesState.entitiesBuffer)
                bindShaderStorageBuffer(4, entityOffsetBuffer)
                bindShaderStorageBuffer(5, commandBuffer)
//                bindShaderStorageBuffer(7, targetCommandBuffer)
                bindShaderStorageBuffer(8, entityOffsetBuffersCulled[phase]!!)
                bindShaderStorageBuffer(9, visibilityBuffers[phase]!!)
                bindShaderStorageBuffer(10, entitiesBuffersCompacted[phase]!!)
                bindShaderStorageBuffer(11, entitiesCompactedCounter[phase]!!)
                bindShaderStorageBuffer(12, commandOffsets[phase]!!)
                bindShaderStorageBuffer(13, currentCompactedPointers[phase]!!)
            }
            setUniform("maxDrawCommands", commandOrganization.commands.size)
            setUniformAsMatrix4("viewProjectionMatrix", renderState.camera.viewProjectionMatrixAsBuffer)
            setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
            setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
            GraphicsContext.getInstance().bindTexture(0, TEXTURE_2D, highZBuffer.renderedTexture)
            GraphicsContext.getInstance().bindImageTexture(1, highZBuffer.renderedTexture, 0, false, 0, GL15.GL_WRITE_ONLY, HIGHZ_FORMAT)
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, (commandOrganization.commands.size + 2) / 3 * 3, invocationsPerCommand)
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT)
            glMemoryBarrier(GL_ALL_BARRIER_BITS)
        }
        GPUProfiler.end()
    }

    private fun drawIndirect(vertexIndexBuffer: VertexIndexBuffer<*>, commandBuffer: CommandBuffer, commandCount: Int, drawCountBuffer: AtomicCounterBuffer) {
        val indexBuffer = vertexIndexBuffer.indexBuffer
        val vertexBuffer = vertexIndexBuffer.vertexBuffer
        if (Config.getInstance().isDrawLines && useLineDrawingIfActivated) {
            GraphicsContext.getInstance().disable(GlCap.CULL_FACE)
            drawLinesInstancedIndirectBaseVertex(vertexBuffer, indexBuffer, commandBuffer, commandCount)
        } else {
            if (useBackfaceCulling) {
                GraphicsContext.getInstance().enable(GlCap.CULL_FACE)
            }
            if (Config.getInstance().isUseOcclusionCulling) {
                multiDrawElementsIndirectCount(vertexBuffer, indexBuffer, commandBuffer, drawCountBuffer, commandCount)
            } else {
                multiDrawElementsIndirect(vertexBuffer, indexBuffer, commandBuffer, commandCount)
            }
        }
    }

    private var verticesCount = 0
    private var entitiesDrawn = 0
    fun prepare(renderState: RenderState) {
        verticesCount = 0
        entitiesDrawn = 0
        with(renderState.commandOrganizationStatic) {
            commands.clear()
            offsets.clear()
            addCommands(renderState.renderBatchesStatic, commands, commandBuffer, entityOffsetBuffer, offsets)
        }
        with(renderState.commandOrganizationAnimated) {
            commands.clear()
            offsets.clear()
            addCommands(renderState.renderBatchesAnimated, commands, commandBuffer, entityOffsetBuffer, offsets)
        }
    }

    private fun addCommands(renderBatches: List<RenderBatch>, commands: MutableList<CommandBuffer.DrawElementsIndirectCommand>, commandBuffer: CommandBuffer, entityOffsetBuffer: IndexBuffer, offsets: IntArrayList) {
        for (i in renderBatches.indices) {
            val info = renderBatches[i]
            if (!info.isVisible || (Config.getInstance().isUseFrustumCulling && useFrustumCulling && !info.isVisibleForCamera)) {
                continue
            }
            commands.add(info.drawElementsIndirectCommand)
            verticesCount += info.vertexCount * info.instanceCount
            if (info.vertexCount > 0) {
                entitiesDrawn += info.instanceCount
            }
            offsets.add(info.drawElementsIndirectCommand.entityOffset)
        }

        entityOffsetBuffer.put(0, offsets.toArray())
        commandBuffer.put(*toArray(commands, CommandBuffer.DrawElementsIndirectCommand::class.java))
    }

    protected open fun beforeDrawStatic(renderState: RenderState, program: Program) {}
    protected open fun beforeDrawAnimated(renderState: RenderState, program: Program) {}

    companion object {
        @JvmField var HIGHZ_FORMAT = GL30.GL_R32F
    }

    enum class CullingPhase(val coarsePhase: CoarseCullingPhase) {
        STATIC_ONE(ONE),
        STATIC_TWO(TWO),
        ANIMATED_ONE(ONE),
        ANIMATED_TWO(TWO)
    }

    enum class CoarseCullingPhase {
        ONE,
        TWO
    }
}

private val Pipeline.CoarseCullingPhase.staticPhase: Pipeline.CullingPhase
    get() {
        return if(this == ONE) STATIC_ONE else STATIC_TWO
    }
private val Pipeline.CoarseCullingPhase.animatedPhase: Pipeline.CullingPhase
    get() {
        return if(this == ONE) ANIMATED_ONE else ANIMATED_TWO
    }
