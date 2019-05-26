package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.renderer.AtomicCounterBuffer
import de.hanno.hpengine.engine.graphics.renderer.DrawDescription
import de.hanno.hpengine.engine.graphics.renderer.Renderer
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawUtils
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.VoxelConeTracingExtension
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetBuilder
import de.hanno.hpengine.engine.graphics.shader.AbstractProgram
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.CommandBuffer
import de.hanno.hpengine.engine.model.IndexBuffer
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import org.lwjgl.opengl.*
import java.io.File

open class GPUFrustumCulledPipeline @JvmOverloads constructor(private val engine: EngineContext<OpenGl>,
                                                              renderer: Renderer<OpenGl>,
                                                              useFrustumCulling: Boolean = true,
                                                              useBackfaceCulling: Boolean = true,
                                                              useLineDrawing: Boolean = true,
                                                              renderCam: Camera? = null,
                                                              cullCam: Camera? = renderCam) : SimplePipeline(engine, cullCam, renderCam, useFrustumCulling, useBackfaceCulling, useLineDrawing) {

    protected open fun getDefines() = Defines(Define.getDefine("FRUSTUM_CULLING", true))

    private var occlusionCullingPhase1Vertex: Program = engine.programManager.getProgram(CodeSource(File(Shader.directory + "occlusion_culling1_vertex.glsl")))
    private var occlusionCullingPhase2Vertex: Program = engine.programManager.getProgram(CodeSource(File(Shader.directory + "occlusion_culling2_vertex.glsl")))

    val appendDrawcommandsProgram = engine.programManager.getProgramFromFileNames("append_drawcommands_vertex.glsl")
    val appendDrawCommandComputeProgram = engine.programManager.getComputeProgram("append_drawcommands_compute.glsl")

    var highZBuffer: RenderTarget = RenderTargetBuilder<RenderTargetBuilder<*,*>, RenderTarget>(engine.gpuContext)
            .setName("GPUCulledPipeline")
            .setWidth(Config.getInstance().width / 2).setHeight(Config.getInstance().height / 2)
            .add(ColorAttachmentDefinition("HighZ").setInternalFormat(Pipeline.HIGHZ_FORMAT)
                    .setTextureFilter(GL11.GL_NEAREST_MIPMAP_NEAREST))
            .build()

    override fun drawStaticAndAnimated(drawDescriptionStatic: DrawDescription, drawDescriptionAnimated: DrawDescription) {
        ARBClearTexture.glClearTexImage(highZBuffer.renderedTexture, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, VoxelConeTracingExtension.ZERO_BUFFER)
        val cullAndRender = { profilerString: String,
                              phase: Pipeline.CoarseCullingPhase ->
            GPUProfiler.start(profilerString)
            cullAndRender(drawDescriptionStatic, { beforeDrawStatic(drawDescriptionStatic.renderState, drawDescriptionStatic.program) }, phase.staticPhase)
            debugPrintPhase1(drawDescriptionStatic, Pipeline.CullingPhase.STATIC_ONE)
            cullAndRender(drawDescriptionAnimated, { beforeDrawAnimated(drawDescriptionAnimated.renderState, drawDescriptionAnimated.program) }, phase.animatedPhase)
            debugPrintPhase1(drawDescriptionAnimated, Pipeline.CullingPhase.ANIMATED_ONE)
            renderHighZMap()
            GPUProfiler.end()
        }
        cullAndRender("Cull&Render Phase1", Pipeline.CoarseCullingPhase.ONE)
    }

    private val highZProgram = engine.programManager.getComputeProgram("highZ_compute.glsl", Defines(Define.getDefine("SOURCE_CHANNEL_R", true)))

    open fun renderHighZMap() {
        DrawUtils.renderHighZMap(engine.gpuContext, depthMap, Config.getInstance().width, Config.getInstance().height, highZBuffer.renderedTexture, highZProgram)
    }

    open var depthMap = renderer.gBuffer.visibilityMap

    private fun debugPrintPhase1(drawDescription: DrawDescription, phase: Pipeline.CullingPhase) {
        if (Config.getInstance().isPrintPipelineDebugOutput) {
            GL11.glFinish()
            println("########### $phase ")

            fun printPhase1(commandOrganization: CommandOrganization) {
                with(commandOrganization) {
                    println("Visibilities")
                    Util.printIntBuffer(visibilityBuffers.intBufferView, if (commands.isEmpty()) 0 else commands.map { it.primCount }.reduce({ a, b -> a + b }), 1)
                    println("Entity instance counts")
                    Util.printIntBuffer(entitiesCounters.intBufferView, commands.size, 1)
                    println("DrawCountBuffer")
                    println(drawCountBuffer.intBufferView.get(0))
                    println("Entities compacted counter")
                    println(entitiesCompactedCounter.intBufferView.get(0))
                    println("Offsets culled")
                    Util.printIntBuffer(entityOffsetBuffersCulled.intBufferView, commands.size, 1)
                    println("CurrentCompactedPointers")
                    Util.printIntBuffer(currentCompactedPointers.intBufferView, commands.size, 1)
                    println("CommandOffsets")
                    Util.printIntBuffer(commandOffsets.intBufferView, commands.size, 1)
                    if(!commands.isEmpty()) {
                        println("Commands")
                        Util.printIntBuffer(commandBuffers.intBufferView, 5, drawCountBuffers.intBufferView.get(0))
                        println("Entities")
                        Util.printModelComponents(drawDescription.renderState.entitiesBuffer.buffer, 10)
                        println("Entities compacted")
                        Util.printModelComponents(entitiesBuffersCompacted.buffer, 10)
                    }
                }
            }

            printPhase1(drawDescription.commandOrganization)
        }
    }

    private fun cullAndRender(drawDescription: DrawDescription, beforeRender: () -> Unit, phase: Pipeline.CullingPhase) {
        with(drawDescription.commandOrganization) {
            val drawCountBuffer = drawCountBuffers
            drawCountBuffer.put(0, 0)
            val entitiesCompactedCounter1 = entitiesCompactedCounter
            entitiesCompactedCounter1.put(0,0)
            if (commands.isEmpty()) {
                return
            }
            val targetCommandBuffer = commandBuffers

            with(drawDescription) {
                cullPhase(renderState, commandOrganization, drawCountBuffer, targetCommandBuffer, phase)
                render(renderState, program, commandOrganization, vertexIndexBuffer, drawCountBuffer, targetCommandBuffer, entityOffsetBuffersCulled, beforeRender, phase)
            }
        }
    }

    private fun cullPhase(renderState: RenderState, commandOrganization: CommandOrganization, drawCountBuffer: AtomicCounterBuffer, targetCommandBuffer: CommandBuffer, phase: Pipeline.CullingPhase) {
        GPUProfiler.start("Culling Phase")
        cull(renderState, commandOrganization, phase)

        drawCountBuffer.put(0, 0)
        val appendProgram: AbstractProgram = if(Config.getInstance().isUseComputeShaderDrawCommandAppend) appendDrawCommandComputeProgram else appendDrawcommandsProgram

        GPUProfiler.start("Buffer compaction")
        with(commandOrganization) {
            commandBuffers.sizeInBytes = commandBuffer.sizeInBytes
            val instanceCount = commands.map { it.primCount }.reduce({ a, b -> a + b })
            visibilityBuffers.sizeInBytes = instanceCount * java.lang.Integer.BYTES
            commandOrganization.entitiesBuffersCompacted.sizeInBytes = instanceCount * ModelComponent.getBytesPerInstance()
            val entitiesCountersToUse = entitiesCounters
            entitiesCountersToUse.sizeInBytes = commands.size * java.lang.Integer.BYTES
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
                setUniform("maxDrawCommands", commands.size)
                if(Config.getInstance().isUseComputeShaderDrawCommandAppend) {
                    appendDrawCommandComputeProgram.dispatchCompute(commands.size, 1, 1)
                } else {
                    val invocationsPerCommand : Int = commands.map { it.primCount }.max()!!//4096
                    GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, (invocationsPerCommand + 2) / 3 * 3, commands.size)
                }
                GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT or GL42.GL_TEXTURE_FETCH_BARRIER_BIT or GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT or GL42.GL_COMMAND_BARRIER_BIT)
                GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
            }
        }
        GPUProfiler.end()
        GPUProfiler.end()
    }

    private fun render(renderState: RenderState, program: Program, commandOrganization: CommandOrganization, vertexIndexBuffer: VertexIndexBuffer, drawCountBuffer: AtomicCounterBuffer, commandBuffer: CommandBuffer, offsetBuffer: IndexBuffer, beforeRender: () -> Unit, phase: Pipeline.CullingPhase) {
        GPUProfiler.start("Actually render")
        program.use()
        beforeRender()
        program.setUniform("entityIndex", 0)
        program.setUniform("entityBaseIndex", 0)
        program.setUniform("indirect", true)
        val drawCountBufferToUse = drawCountBuffer
        if(Config.getInstance().isUseGpuOcclusionCulling) {
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
        drawIndirect(vertexIndexBuffer, commandBuffer, commandOrganization.commands.size, drawCountBufferToUse)
        GPUProfiler.end()
    }

    private fun cull(renderState: RenderState, commandOrganization: CommandOrganization, phase: Pipeline.CullingPhase) {
        GPUProfiler.start("Visibility detection")
        val occlusionCullingPhase = if (phase.coarsePhase == Pipeline.CoarseCullingPhase.ONE) occlusionCullingPhase1Vertex else occlusionCullingPhase2Vertex
        with(occlusionCullingPhase) {
//            commandOrganization.commands.map { it.primCount }.reduce({ a, b -> a + b })
            val invocationsPerCommand : Int = commandOrganization.commands.map { it.primCount }.max()!!//4096
            use()
            with(commandOrganization) {
                bindShaderStorageBuffer(1, entitiesCounters)
                bindShaderStorageBuffer(2, drawCountBuffer)
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
            setUniform("maxDrawCommands", commandOrganization.commands.size)
            val camera = cullCam ?: renderState.camera
            setUniformAsMatrix4("viewProjectionMatrix", camera.viewProjectionMatrixAsBuffer)
            setUniformAsMatrix4("viewMatrix", camera.viewMatrixAsBuffer)
            setUniform("camPosition", camera.entity.position)
            setUniformAsMatrix4("projectionMatrix", camera.projectionMatrixAsBuffer)
            engine.gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, highZBuffer.renderedTexture)
            engine.gpuContext.bindImageTexture(1, highZBuffer.renderedTexture, 0, false, 0, GL15.GL_WRITE_ONLY, Pipeline.HIGHZ_FORMAT)
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, (commandOrganization.commands.size + 2) / 3 * 3, invocationsPerCommand)
            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT)
            GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
        }
        GPUProfiler.end()
    }

}