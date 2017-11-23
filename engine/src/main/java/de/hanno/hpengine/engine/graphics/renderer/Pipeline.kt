package de.hanno.hpengine.engine.graphics.renderer

import com.carrotsearch.hppc.IntArrayList
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer
import de.hanno.hpengine.engine.graphics.renderer.Pipeline.CullingPhase.ONE
import de.hanno.hpengine.engine.graphics.renderer.Pipeline.CullingPhase.TWO
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
import de.hanno.hpengine.engine.model.IndexBuffer
import de.hanno.hpengine.engine.model.VertexBuffer.*
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.util.Util.printIntBuffer
import de.hanno.hpengine.util.Util.toArray
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.glFinish
import org.lwjgl.opengl.GL42.*
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT
import java.lang.Byte.toUnsignedInt

open class Pipeline @JvmOverloads constructor(private val useFrustumCulling: Boolean = true, private val useBackfaceCulling: Boolean = true, private val useLineDrawingIfActivated: Boolean = true) {
    protected val commandOrganizationStatic = CommandOrganization()
    protected val commandOrganizationAnimated = CommandOrganization()
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
        drawStaticAndAnimated(DrawDescription(renderState, programStatic, commandOrganizationStatic, renderState.vertexIndexBufferStatic),
                DrawDescription(renderState, programAnimated, commandOrganizationAnimated, renderState.vertexIndexBufferAnimated))

        firstPassResult.verticesDrawn += verticesCount
        firstPassResult.entitiesDrawn += entitiesDrawn
        GPUProfiler.end()
    }

    protected open fun drawStaticAndAnimated(drawDescriptionStatic: DrawDescription, drawDescriptionAnimated: DrawDescription) {
        if (Config.getInstance().isUseOcclusionCulling) {

            ARBClearTexture.glClearTexImage(highZBuffer.renderedTexture, 0, GL_RGBA, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER)

            val cullAndRender = { profilerString: String,
                                  phase: CullingPhase ->
                GPUProfiler.start(profilerString)
                cullAndRender(drawDescriptionStatic, { beforeDrawStatic(drawDescriptionStatic.renderState, drawDescriptionStatic.program) }, phase)
                cullAndRender(drawDescriptionAnimated, { beforeDrawAnimated(drawDescriptionAnimated.renderState, drawDescriptionAnimated.program) }, phase)
                renderHighZMap(Renderer.getInstance().gBuffer.visibilityMap, Config.getInstance().width, Config.getInstance().height, highZBuffer.renderedTexture, ProgramFactory.getInstance().highZProgram)
                GPUProfiler.end()
            }

            cullAndRender("Cull&Render Phase1", ONE)
            cullAndRender("Cull&Render Phase2", TWO)

            printDebugOutput(drawDescriptionStatic.commandOrganization, drawDescriptionAnimated.commandOrganization)
        } else {
            val drawCountBuffer = drawDescriptionStatic.commandOrganization.drawCountBufferStatic
            with(drawDescriptionStatic) {
                with(commandOrganizationStatic) {
                    drawCountBuffer.put(0, commands.size)
                    render(renderState, program, this@with, vertexIndexBuffer, drawCountBuffer, commandBuffer, entityOffsetBuffer) {
                        beforeDrawStatic(renderState, program)
                    }
                }
            }
            with(drawDescriptionAnimated) {
                with(commandOrganizationAnimated) {
                    drawCountBuffer.put(0, commands.size)
                    render(renderState, program, this@with, vertexIndexBuffer, drawCountBuffer, commandBuffer, entityOffsetBuffer) {
                        beforeDrawAnimated(renderState, program)
                    }
                }
            }
        }
    }

    private fun cullAndRender(drawDescription: DrawDescription, beforeRender: () -> Unit, phase: CullingPhase) {
        with(drawDescription.commandOrganization) {
            val drawCountBuffer = if (phase == ONE) drawCountBufferPhase1 else drawCountBufferPhase2
            drawCountBuffer.put(0, 0)
            if (commands.isEmpty()) {
                return
            }

            val targetCommandBuffer = if (phase == ONE) commandBufferCulledPhase1 else commandBufferCulledPhase2
            with(drawDescription) {
                cullPhase(renderState, commandOrganization, drawCountBuffer, targetCommandBuffer, phase)
                render(renderState, program, commandOrganization, vertexIndexBuffer, drawCountBuffer, targetCommandBuffer, entityOffsetBufferCulled, beforeRender)
            }
        }
    }

    private fun cullPhase(renderState: RenderState, commandOrganization: CommandOrganization, drawCountBuffer: AtomicCounterBuffer, targetCommandBuffer: CommandBuffer, phase: CullingPhase) {
        GPUProfiler.start("Culling Phase")
        cull(renderState, commandOrganization, targetCommandBuffer, phase)

        drawCountBuffer.put(0, 0)
        val appendProgram = ProgramFactory.getInstance().appendDrawCommandProgram
        with(commandOrganization) {
            with(appendProgram) {
                use()
                bindShaderStorageBuffer(2, drawCountBuffer)
                bindShaderStorageBuffer(3, renderState.entitiesState.entitiesBuffer)
                bindShaderStorageBuffer(4, entityOffsetBuffer)
                bindShaderStorageBuffer(5, commandBuffer)
                bindShaderStorageBuffer(7, targetCommandBuffer)
                bindShaderStorageBuffer(8, entityOffsetBufferCulled)
                setUniform("maxDrawCommands", commands.size)
                commandBufferCulledPhase1.sizeInBytes = commandBuffer.sizeInBytes
                commandBufferCulledPhase2.sizeInBytes = commandBuffer.sizeInBytes
                GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, (commands.size + 2) / 3 * 3, 1)
                glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT or GL_TEXTURE_FETCH_BARRIER_BIT or GL_SHADER_IMAGE_ACCESS_BARRIER_BIT or GL_COMMAND_BARRIER_BIT)
            }
        }
        GPUProfiler.end()
    }

    private fun render(renderState: RenderState, program: Program, commandOrganization: CommandOrganization, vertexIndexBuffer: VertexIndexBuffer<*>, drawCountBuffer: AtomicCounterBuffer, commandBuffer: CommandBuffer, offsetBuffer: IndexBuffer, beforeRender: () -> Unit) {
        GPUProfiler.start("Actually render")
        program.use()
        beforeRender()
        if (Config.getInstance().isIndirectRendering) {
            program.setUniform("entityIndex", 0)
            program.setUniform("entityBaseIndex", 0)
            program.setUniform("indirect", true)
            program.bindShaderStorageBuffer(4, offsetBuffer)
            program.bindShaderStorageBuffer(6, renderState.entitiesState.jointsBuffer)
            drawIndirect(vertexIndexBuffer, commandBuffer, commandOrganization.commands.size, drawCountBuffer)
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

    private fun cull(renderState: RenderState, commandOrganization: CommandOrganization, targetCommandBuffer: CommandBuffer, phase: CullingPhase) {
        val occlusionCullingPhase = if (phase == ONE) occlusionCullingPhase1Vertex else occlusionCullingPhase2Vertex
        with(occlusionCullingPhase) {
            use()
            bindShaderStorageBuffer(3, renderState.entitiesState.entitiesBuffer)
            bindShaderStorageBuffer(4, commandOrganization.entityOffsetBuffer)
            bindShaderStorageBuffer(5, commandOrganization.commandBuffer)
            bindShaderStorageBuffer(6, renderState.entitiesState.jointsBuffer)
            bindShaderStorageBuffer(7, targetCommandBuffer)
            bindShaderStorageBuffer(8, commandOrganization.entityOffsetBufferCulled)
            bindShaderStorageBuffer(9, commandOrganization.visibilityBuffer)
            setUniform("maxDrawCommands", commandOrganization.commands.size)
            setUniformAsMatrix4("viewProjectionMatrix", renderState.camera.viewProjectionMatrixAsBuffer)
            setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
            setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
            GraphicsContext.getInstance().bindTexture(0, TEXTURE_2D, highZBuffer.renderedTexture)
            GraphicsContext.getInstance().bindImageTexture(1, highZBuffer.renderedTexture, 0, false, 0, GL15.GL_WRITE_ONLY, HIGHZ_FORMAT)
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, (commandOrganization.commands.size + 2) / 3 * 3, 1)
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT)
        }
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
        with(commandOrganizationStatic) {
            commands.clear()
            offsets.clear()
            addCommands(renderState.renderBatchesStatic, commands, commandBuffer, entityOffsetBuffer, offsets)
        }
        with(commandOrganizationAnimated) {
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

    val entityOffsetBufferStatic: GPUBuffer<*>
        get() = commandOrganizationStatic.entityOffsetBuffer

    val entityOffsetBufferAnimated: IndexBuffer
        get() = commandOrganizationAnimated.entityOffsetBuffer

    protected open fun beforeDrawStatic(renderState: RenderState, program: Program) {}
    protected open fun beforeDrawAnimated(renderState: RenderState, program: Program) {}

    private fun printDebugOutput(commandOrganizationStatic: CommandOrganization, commandOrganizationAnimated: CommandOrganization) {
        if (Config.getInstance().isPrintPipelineDebugOutput) {
            glFinish()
            println("######################")
            val printBuffers = true
            val printOrganization = { headline: String,
                                      commandOrganization: CommandOrganization ->
                with(commandOrganization) {
                    println("####### $headline")
                    println("commands came in: " + commands.size)
                    println("drawCountBufferPhase1: " + toUnsignedInt(drawCountBufferPhase1.buffer.get(0)))
                    println("false culled: " + toUnsignedInt(drawCountBufferPhase2.buffer.get(0)))
                    if (printBuffers) {
                        println("Command buffer:")
                        printIntBuffer(commandBuffer.buffer.asIntBuffer(), 5, commands.size)
                        println("Command buffer culled phase 1:")
                        printIntBuffer(commandBufferCulledPhase1.buffer.asIntBuffer(), 5, toUnsignedInt(drawCountBufferPhase1.buffer.get(0)))
                        println("Command buffer culled phase 2:")
                        printIntBuffer(commandBufferCulledPhase2.buffer.asIntBuffer(), 5, toUnsignedInt(drawCountBufferPhase2.buffer.get(0)))
                        print("Offsets ")
                        printIntBuffer(entityOffsetBuffer.buffer.asIntBuffer(), commands.size, 1)
                        print("Offsets culled ")
                        printIntBuffer(entityOffsetBufferCulled.buffer.asIntBuffer(), commands.size, 1)
                    }
                }
            }
            printOrganization("STATIC", commandOrganizationStatic)
            printOrganization("ANIMATED", commandOrganizationAnimated)
        }
    }

    companion object {
        @JvmField var HIGHZ_FORMAT = GL30.GL_R32F
    }

    private enum class CullingPhase {
        ONE,
        TWO
    }
}
