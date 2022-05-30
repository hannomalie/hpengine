package de.hanno.hpengine.engine.graphics.renderer.pipelines

import DrawElementsIndirectCommandStruktImpl.Companion.sizeInBytes
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.*
import de.hanno.hpengine.engine.graphics.renderer.IndirectDrawDescription
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.RenderingMode.Faces
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.RenderingMode.Lines
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.useAndBind
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.vertexbuffer.multiDrawElementsIndirectCount

open class IndirectPipeline @JvmOverloads constructor(
    private val config: Config,
    private val gpuContext: GpuContext<OpenGl>,
    private val useBackFaceCulling: Boolean = true
) : Pipeline {

    protected var verticesCount = 0
    protected var entitiesCount = 0
    internal var commandOrganizationStatic = CommandOrganization(gpuContext)
    internal var commandOrganizationAnimated = CommandOrganization(gpuContext)

    init {
//         TODO: This prevents runtime pipeline selection...
//        require(gpuContext.isSupported(BindlessTextures)) { "Cannot use indirect pipeline without bindless textures feature" }
//        require(gpuContext.isSupported(DrawParameters)) { "Cannot use indirect pipeline without drawcount buffer" }
    }

    override fun prepare(renderState: RenderState) {
        prepare(renderState, renderState.camera)
    }

    fun prepare(renderState: RenderState, camera: Camera) {
        if(config.debug.freezeCulling) return
        verticesCount = 0
        entitiesCount = 0

        fun CommandOrganization.prepare(batches: List<RenderBatch>) {
            filteredRenderBatches =
                batches.filterNot { it.isCulledOrForwardRendered(camera) }.filterNot { it.hasOwnProgram }
            commandCount = filteredRenderBatches.size
            addCommands(filteredRenderBatches, commandBuffer, entityOffsetBuffer)

        }

        commandOrganizationStatic.prepare(renderState.renderBatchesStatic)
        commandOrganizationAnimated.prepare(renderState.renderBatchesAnimated)

    }

    override fun draw(renderState: RenderState,
                      programStatic: Program<StaticFirstPassUniforms>,
                      programAnimated: Program<AnimatedFirstPassUniforms>,
                      firstPassResult: FirstPassResult) = profiled("Actual draw entities") {

        val mode = if (config.debug.isDrawLines) Lines else Faces
        IndirectDrawDescription(
            renderState,
            programStatic,
            commandOrganizationStatic,
            renderState.vertexIndexBufferStatic,
            this::beforeDrawStatic,
            mode,
            renderState.camera
        ).draw()
        IndirectDrawDescription(
            renderState,
            programAnimated,
            commandOrganizationAnimated,
            renderState.vertexIndexBufferAnimated,
            this::beforeDrawAnimated,
            mode,
            renderState.camera
        ).draw()

        firstPassResult.verticesDrawn += verticesCount
        firstPassResult.entitiesDrawn += entitiesCount

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

}

fun addCommands(
    renderBatches: List<RenderBatch>,
    commandBuffer: PersistentTypedBuffer<DrawElementsIndirectCommandStrukt>,
    entityOffsetBuffer: PersistentMappedStructBuffer<IntStruct>
) {

    val resultingCommandCount = renderBatches.size
    entityOffsetBuffer.enlarge(resultingCommandCount)
    entityOffsetBuffer.buffer.rewind()
    commandBuffer.persistentMappedBuffer.enlarge(resultingCommandCount * DrawElementsIndirectCommandStrukt.sizeInBytes)
    commandBuffer.persistentMappedBuffer.buffer.rewind()

    commandBuffer.typedBuffer.byteBuffer.run {
        var index = 0
        for (batch in renderBatches) {

            commandBuffer.typedBuffer[index].run {
                count = batch.drawElementsIndirectCommand.count
                instanceCount = batch.drawElementsIndirectCommand.instanceCount
                firstIndex = batch.drawElementsIndirectCommand.firstIndex
                baseVertex = batch.drawElementsIndirectCommand.baseVertex
                baseInstance = batch.drawElementsIndirectCommand.baseInstance
            }
            entityOffsetBuffer[index].value = batch.entityBufferIndex

            index += 1
        }
    }
//    if(renderBatches.isNotEmpty()) {
//        commandBuffer.typedBuffer.forEach(renderBatches.size) { println(it.print()) }
//        repeat(renderBatches.size) {
//            print(entityOffsetBuffer[it].value.toString() + ",")
//        }
//        println()
//    }
    entityOffsetBuffer.buffer.rewind()
    commandBuffer.typedBuffer.byteBuffer.rewind()
}

fun <T: FirstPassUniforms> IndirectDrawDescription<T>.draw() {
    beforeDraw(renderState, program, drawCam)
    with(commandOrganization) {
        drawCountBuffer.put(0, commandCount)
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

                vertexIndexBuffer.multiDrawElementsIndirectCount(commandBuffer, drawCountBuffer, 0, commandCount, mode)
            }
        }
    }
}
