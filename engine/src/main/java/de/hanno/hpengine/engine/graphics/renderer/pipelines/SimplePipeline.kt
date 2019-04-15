package de.hanno.hpengine.engine.graphics.renderer.pipelines

import com.carrotsearch.hppc.IntArrayList
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.renderer.AtomicCounterBuffer
import de.hanno.hpengine.engine.graphics.renderer.DrawDescription
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.CommandBuffer
import de.hanno.hpengine.engine.model.IndexBuffer
import de.hanno.hpengine.engine.model.VertexBuffer
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import de.hanno.struct.Struct
import de.hanno.struct.StructArray
import de.hanno.struct.copyTo

open class SimplePipeline @JvmOverloads constructor(private val engine: EngineContext<OpenGl>,
                                                    private val useFrustumCulling: Boolean = true,
                                                    private val useBackFaceCulling: Boolean = true,
                                                    private val useLineDrawingIfActivated: Boolean = true) : Pipeline {

    private var verticesCount = 0
    private var entitiesDrawn = 0
    protected open fun beforeDrawStatic(renderState: RenderState, program: Program) {}
    protected open fun beforeDrawAnimated(renderState: RenderState, program: Program) {}

    private var gpuCommandsArray = StructArray(1000) { Command() }

    override fun prepare(renderState: RenderState) {
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

    override fun prepareAndDraw(renderState: RenderState, programStatic: Program, programAnimated: Program, firstPassResult: FirstPassResult) {
        prepare(renderState)
        draw(renderState, programStatic, programAnimated, firstPassResult)
    }


    override fun draw(renderState: RenderState, programStatic: Program, programAnimated: Program, firstPassResult: FirstPassResult) {
        GPUProfiler.start("Actual draw entities")
        with(renderState) {
            drawStaticAndAnimated(DrawDescription(renderState, programStatic, commandOrganizationStatic, renderState.vertexIndexBufferStatic),
                    DrawDescription(renderState, programAnimated, commandOrganizationAnimated, renderState.vertexIndexBufferAnimated))

            firstPassResult.verticesDrawn += verticesCount
            firstPassResult.entitiesDrawn += entitiesDrawn
        }
        GPUProfiler.end()
    }

    private fun render(renderState: RenderState, program: Program, commandOrganization: CommandOrganization, vertexIndexBuffer: VertexIndexBuffer, drawCountBuffer: AtomicCounterBuffer, commandBuffer: CommandBuffer, offsetBuffer: IndexBuffer, beforeRender: () -> Unit) {
        GPUProfiler.start("Actually render")
        program.use()
        beforeRender()
        if (Config.getInstance().isIndirectRendering) {
            program.setUniform("entityIndex", 0)
            program.setUniform("entityBaseIndex", 0)
            program.setUniform("indirect", true)
            program.bindShaderStorageBuffer(3, renderState.entitiesState.entitiesBuffer)
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
    protected open fun drawStaticAndAnimated(drawDescriptionStatic: DrawDescription, drawDescriptionAnimated: DrawDescription) {
        val drawCountBuffer = drawDescriptionStatic.commandOrganization.drawCountBuffer
        with(drawDescriptionStatic) {
            with(drawDescriptionStatic.commandOrganization) {
                drawCountBuffer.put(0, commands.size)
                render(renderState, program, this@with, vertexIndexBuffer, drawCountBuffer, commandBuffer, entityOffsetBuffer) {
                    beforeDrawStatic(renderState, program)
                }
            }
        }
        with(drawDescriptionAnimated) {
            with(drawDescriptionAnimated.commandOrganization) {
                drawCountBuffer.put(0, commands.size)
                render(renderState, program, this@with, vertexIndexBuffer, drawCountBuffer, commandBuffer, entityOffsetBuffer) {
                    beforeDrawAnimated(renderState, program)
                }
            }
        }
    }

    protected fun drawIndirect(vertexIndexBuffer: VertexIndexBuffer, commandBuffer: CommandBuffer, commandCount: Int, drawCountBuffer: AtomicCounterBuffer) {
        val indexBuffer = vertexIndexBuffer.indexBuffer
        val vertexBuffer = vertexIndexBuffer.vertexBuffer
        if (Config.getInstance().isDrawLines && useLineDrawingIfActivated) {
            engine.gpuContext.disable(GlCap.CULL_FACE)
            VertexBuffer.drawLinesInstancedIndirectBaseVertex(vertexBuffer, indexBuffer, commandBuffer, commandCount)
        } else {
            if (useBackFaceCulling) {
                engine.gpuContext.enable(GlCap.CULL_FACE)
            }
//            VertexBuffer.multiDrawElementsIndirect(vertexBuffer, indexBuffer, commandBuffer, commandCount)
            VertexBuffer.multiDrawElementsIndirectCount(vertexBuffer, indexBuffer, commandBuffer, drawCountBuffer, commandCount)
        }
    }

    private fun addCommands(renderBatches: List<RenderBatch>, commands: MutableList<CommandBuffer.DrawElementsIndirectCommand>, commandBuffer: CommandBuffer, entityOffsetBuffer: IndexBuffer, offsets: IntArrayList) {
        for (i in renderBatches.indices) {
            val info = renderBatches[i]
            val culled = Config.getInstance().isUseCpuFrustumCulling && useFrustumCulling && !info.isVisibleForCamera
            val isForward = info.materialInfo.transparencyType.needsForwardRendering
            if (!info.isVisible || culled || isForward) {
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

        commandBuffer.setCapacityInBytes((commands.size) * CommandBuffer.DrawElementsIndirectCommand.sizeInBytes())
        commandBuffer.buffer.rewind()

        for ((i, command) in commands.withIndex()) {
            val target = gpuCommandsArray.getAtIndex(i)
            target.baseInstance = command.baseInstance
            target.baseVertex = command.baseVertex
            target.count = command.count
            target.firstIndex = command.firstIndex
            target.primCount = command.primCount
        }

        gpuCommandsArray.buffer.copyTo(commandBuffer.buffer)

        commandBuffer.buffer.rewind()
    }

    override fun update(writeState: RenderState) {
        prepare(writeState)
    }
}

class Command : Struct(){
    var count by 0
    var primCount by 0
    var firstIndex by 0
    var baseVertex by 0
    var baseInstance by 0
}

