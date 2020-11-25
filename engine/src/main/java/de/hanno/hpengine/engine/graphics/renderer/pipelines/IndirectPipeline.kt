package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.graphics.BindlessTextures
import de.hanno.hpengine.engine.graphics.DrawParameters
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.IndirectDrawDescription
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.PrimitiveMode.Lines
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.PrimitiveMode.Triangles
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.vertexbuffer.multiDrawElementsIndirectCount
import de.hanno.struct.copyTo

open class IndirectPipeline @JvmOverloads constructor(private val engine: EngineContext,
                                                      private val useFrustumCulling: Boolean = true,
                                                      private val useBackFaceCulling: Boolean = true,
                                                      private val useLineDrawingIfActivated: Boolean = true) : Pipeline {

    protected var verticesCount = 0
    protected var entitiesCount = 0
    protected var commandOrganizationStatic = CommandOrganization(engine.gpuContext)
    protected var commandOrganizationAnimated = CommandOrganization(engine.gpuContext)

    init {
        require(engine.gpuContext.isSupported(BindlessTextures)) { "Cannot use indirect pipeline without bindless textures feature" }
        require(engine.gpuContext.isSupported(DrawParameters)) { "Cannot use indirect pipeline without drawcount buffer" }
    }

    override fun prepare(renderState: RenderState) = prepare(renderState, renderState.camera)

    fun prepare(renderState: RenderState, camera: Camera) {
        verticesCount = 0
        entitiesCount = 0

        fun CommandOrganization.prepare(batches: List<RenderBatch>) {
            filteredRenderBatches = batches.filter { !it.shouldBeSkipped(camera) }
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

        val mode = if (engine.config.debug.isDrawLines) Lines else Triangles
        IndirectDrawDescription(renderState, renderState.renderBatchesStatic, programStatic, commandOrganizationStatic, renderState.vertexIndexBufferStatic, this::beforeDrawStatic, mode, renderState.camera).draw()
        IndirectDrawDescription(renderState, renderState.renderBatchesAnimated, programAnimated, commandOrganizationAnimated, renderState.vertexIndexBufferAnimated, this::beforeDrawAnimated, mode, renderState.camera).draw()

        firstPassResult.verticesDrawn += verticesCount
        firstPassResult.entitiesDrawn += entitiesCount
    }

    override fun beforeDrawStatic(renderState: RenderState, program: Program<StaticFirstPassUniforms>, renderCam: Camera) {
        beforeDraw(renderState, program, renderState.vertexIndexBufferStatic.vertexStructArray, renderCam)
    }

    override fun beforeDrawAnimated(renderState: RenderState, program: Program<AnimatedFirstPassUniforms>, renderCam: Camera) {
        beforeDraw(renderState, program, renderState.vertexIndexBufferAnimated.animatedVertexStructArray, renderCam)
    }

    fun beforeDraw(renderState: RenderState, program: Program<out FirstPassUniforms>,
                   vertexBuffer: PersistentMappedStructBuffer<*>, renderCam: Camera) {
        engine.gpuContext.cullFace = useBackFaceCulling
        program.use()
        program.setUniforms(renderState, renderCam, engine.config)
    }

}

fun addCommands(renderBatches: List<RenderBatch>,
                commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>,
                entityOffsetBuffer: PersistentMappedStructBuffer<IntStruct>) {

    val resultingCommandCount = renderBatches.sumBy{ it.instanceCount }
    entityOffsetBuffer.enlarge(resultingCommandCount)
    commandBuffer.enlarge(resultingCommandCount)

    var index = 0
    for (batch in renderBatches) {
        for(instanceIndex in 0 until batch.instanceCount) {
            batch.drawElementsIndirectCommand.copyTo(commandBuffer[index])
            commandBuffer[index].primCount = 1
            entityOffsetBuffer[index].value = batch.entityBufferIndex + instanceIndex
            index++
        }
    }
}

fun <T: FirstPassUniforms> IndirectDrawDescription<T>.draw() {
    beforeDraw(renderState, program, drawCam)
    with(commandOrganization) {
        drawCountBuffer.put(0, commandCount)
        profiled("Actually render") {
            val uniforms: FirstPassUniforms = program.uniforms
            uniforms.entityIndex = 0
            uniforms.entityBaseIndex = 0
            uniforms.indirect = true
            uniforms.entities = renderState.entitiesState.entitiesBuffer
            uniforms.entityOffsets = entityOffsetBuffer
            when(uniforms) {
                is StaticFirstPassUniforms -> Unit
                is AnimatedFirstPassUniforms -> uniforms.joints = renderState.entitiesState.jointsBuffer
            }!!

            vertexIndexBuffer.multiDrawElementsIndirectCount(commandBuffer, drawCountBuffer, 0, commandCount, mode)
        }
    }
}
