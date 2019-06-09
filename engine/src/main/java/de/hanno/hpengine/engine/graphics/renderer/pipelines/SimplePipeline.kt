package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.BindlessTextures
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.AtomicCounterBuffer
import de.hanno.hpengine.engine.graphics.renderer.DrawDescription
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawUtils
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.CommandBuffer
import de.hanno.hpengine.engine.model.IndexBuffer
import de.hanno.hpengine.engine.model.VertexBuffer
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.model.texture.TextureDimension2D
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.struct.Struct
import de.hanno.struct.StructArray
import de.hanno.struct.copyTo

open class SimplePipeline @JvmOverloads constructor(private val engine: EngineContext<OpenGl>,
                                                    open val renderCam: Camera? = null,
                                                    open val cullCam: Camera? = renderCam,
                                                    private val useFrustumCulling: Boolean = true,
                                                    private val useBackFaceCulling: Boolean = true,
                                                    private val useLineDrawingIfActivated: Boolean = true) : Pipeline {

    private var verticesCount = 0
    private var entitiesDrawn = 0

    private val useIndirectRendering = engine.config.isIndirectRendering && engine.gpuContext.isSupported(BindlessTextures)

    private var gpuCommandsArray = StructArray(1000) { Command() }

    override fun prepare(renderState: RenderState) {
        verticesCount = 0
        entitiesDrawn = 0
        fun addCommands(commandOrganization: CommandOrganization, batches: List<RenderBatch>) = with(commandOrganization) {
            commands.clear()
            offsets.clear()
            addCommands(batches, commands, commandBuffer, entityOffsetBuffer)
        }

        addCommands(renderState.commandOrganizationStatic, renderState.renderBatchesStatic)
        addCommands(renderState.commandOrganizationAnimated, renderState.renderBatchesAnimated)
    }

    override fun prepareAndDraw(renderState: RenderState, programStatic: Program, programAnimated: Program, firstPassResult: FirstPassResult) {
        prepare(renderState)
        draw(renderState, programStatic, programAnimated, firstPassResult)
    }


    override fun draw(renderState: RenderState,
                      programStatic: Program,
                      programAnimated: Program,
                      firstPassResult: FirstPassResult) = profiled("Actual draw entities") {
        with(renderState) {
            drawStaticAndAnimated(
                    DrawDescription(renderState, programStatic, commandOrganizationStatic, renderState.vertexIndexBufferStatic),
                    DrawDescription(renderState, programAnimated, commandOrganizationAnimated, renderState.vertexIndexBufferAnimated))

            firstPassResult.verticesDrawn += verticesCount
            firstPassResult.entitiesDrawn += entitiesDrawn
        }
    }

    protected open fun drawStaticAndAnimated(drawDescriptionStatic: DrawDescription, drawDescriptionAnimated: DrawDescription) {
        if (useIndirectRendering) {
            drawStaticAndAnimatedIndirect(drawDescriptionStatic, drawDescriptionAnimated)
        } else {
            drawStaticAndAnimatedDirect(drawDescriptionStatic, drawDescriptionAnimated)
        }
    }

    private fun drawStaticAndAnimatedIndirect(drawDescriptionStatic: DrawDescription, drawDescriptionAnimated: DrawDescription) {
        // This can be reused ?? To be checked...
        val drawCountBuffer = drawDescriptionStatic.commandOrganization.drawCountBuffer
        fun DrawDescription.drawIndirect() {
            with(commandOrganization) {
                drawCountBuffer.put(0, commands.size)
                profiled("Actually render") {
                    program.setUniform("entityIndex", 0)
                    program.setUniform("entityBaseIndex", 0)
                    program.setUniform("indirect", true)
                    program.bindShaderStorageBuffer(3, renderState.entitiesState.entitiesBuffer)
                    program.bindShaderStorageBuffer(4, entityOffsetBuffer)
                    program.bindShaderStorageBuffer(6, renderState.entitiesState.jointsBuffer)
                    drawIndirect(vertexIndexBuffer, commandBuffer, commands.size, drawCountBuffer)
                }
            }
        }
        fun DrawDescription.prepareAndDrawIndirect(beforeDrawAction: (RenderState, Program) -> Unit) {
            beforeDrawAction(renderState, program)
            drawIndirect()
        }

        drawDescriptionStatic.prepareAndDrawIndirect(::beforeDrawStatic)
        drawDescriptionAnimated.prepareAndDrawIndirect(::beforeDrawAnimated)
    }

    private fun drawStaticAndAnimatedDirect(drawDescriptionStatic: DrawDescription,
                                            drawDescriptionAnimated: DrawDescription) {
        fun DrawDescription.drawHelper(renderBatches: RenderBatch.RenderBatches) {
            program.use()
            for (batch in renderBatches) {
                if (batch.shouldBeSkipped()) continue

                program.setTextureUniforms(engine.gpuContext, batch.materialInfo.maps)
                DrawUtils.draw(engine.gpuContext, renderState, batch, program, engine.config.isDrawLines)
            }
        }
        beforeDrawStatic(drawDescriptionStatic.renderState, drawDescriptionStatic.program)
        drawDescriptionStatic.drawHelper(drawDescriptionStatic.renderState.entitiesState.renderBatchesStatic)

        beforeDrawAnimated(drawDescriptionAnimated.renderState, drawDescriptionAnimated.program)
        drawDescriptionAnimated.drawHelper(drawDescriptionAnimated.renderState.entitiesState.renderBatchesAnimated)
    }

    protected fun drawIndirect(vertexIndexBuffer: VertexIndexBuffer,
                               commandBuffer: CommandBuffer,
                               commandCount: Int,
                               drawCountBuffer: AtomicCounterBuffer) {

        if (engine.config.isDrawLines && useLineDrawingIfActivated) {
            engine.gpuContext.disable(GlCap.CULL_FACE)
            VertexBuffer.drawLinesInstancedIndirectBaseVertex(vertexIndexBuffer, commandBuffer, commandCount)
        } else {
            VertexBuffer.multiDrawElementsIndirectCount(vertexIndexBuffer, commandBuffer, drawCountBuffer, commandCount)
        }
    }

    private fun addCommands(renderBatches: List<RenderBatch>,
                            commands: MutableList<CommandBuffer.DrawElementsIndirectCommand>,
                            commandBuffer: CommandBuffer,
                            entityOffsetBuffer: IndexBuffer) {

        for((index, batch) in renderBatches.filter { !it.shouldBeSkipped() }.withIndex()) {
            commands.add(batch.drawElementsIndirectCommand)
            verticesCount += batch.vertexCount * batch.instanceCount
            entitiesDrawn += batch.instanceCount
            entityOffsetBuffer.put(index, batch.drawElementsIndirectCommand.entityOffset)
        }

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

    fun RenderBatch.shouldBeSkipped(): Boolean {
        val culled = engine.config.isUseCpuFrustumCulling && useFrustumCulling && !isVisibleForCamera
        val isForward = materialInfo.transparencyType.needsForwardRendering
        return !isVisible || culled || isForward
    }

    override fun update(writeState: RenderState) {
        prepare(writeState)
    }

    fun beforeDrawStatic(renderState: RenderState, program: Program) {
        beforeDraw(renderState, program)
    }

    fun beforeDrawAnimated(renderState: RenderState, program: Program) {
        beforeDraw(renderState, program)
    }

    fun beforeDraw(renderState: RenderState, program: Program) {
        if (useBackFaceCulling) {
            engine.gpuContext.enable(GlCap.CULL_FACE)
        }
        program.use()
        program.setUniforms(renderState, cullCam ?: renderCam ?: renderState.camera, engine.config)
    }

}

fun Program.setUniforms(renderState: RenderState, camera: Camera, config: Config) = profiled("setUniforms") {

    val viewMatrixAsBuffer = camera.viewMatrixAsBuffer
    val projectionMatrixAsBuffer = camera.projectionMatrixAsBuffer
    val viewProjectionMatrixAsBuffer = camera.viewProjectionMatrixAsBuffer

    use()
    bindShaderStorageBuffer(1, renderState.materialBuffer)
    bindShaderStorageBuffer(3, renderState.entitiesBuffer)
    setUniform("useRainEffect", config.rainEffect != 0.0f)
    setUniform("rainEffect", config.rainEffect)
    setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer)
    setUniformAsMatrix4("lastViewMatrix", viewMatrixAsBuffer)
    setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer)
    setUniformAsMatrix4("viewProjectionMatrix", viewProjectionMatrixAsBuffer)

    setUniform("eyePosition", camera.getPosition())
    setUniform("near", camera.getNear())
    setUniform("far", camera.getFar())
    setUniform("timeGpu", System.currentTimeMillis().toInt())
    setUniform("useParallax", config.isUseParallax)
    setUniform("useSteepParallax", config.isUseSteepParallax)
}

fun Program.setTextureUniforms(gpuContext: GpuContext<OpenGl>,
                               maps: Map<SimpleMaterial.MAP, Texture<TextureDimension2D>>) {
    for (map in maps) {
        val uniformKey = "has" + map.key.shaderVariableName[0].toUpperCase() + map.key.shaderVariableName.substring(1)
        if (map.value.textureId > 0) {
            gpuContext.bindTexture(map.key.textureSlot, map.value)
            setUniform(uniformKey, true)
        } else {
            setUniform(uniformKey, false)
        }
    }
}

class Command : Struct() {
    var count by 0
    var primCount by 0
    var firstIndex by 0
    var baseVertex by 0
    var baseInstance by 0
}

