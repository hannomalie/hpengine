package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.BindlessTextures
import de.hanno.hpengine.engine.graphics.DrawParameters
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.IndirectDrawDescription
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.texture.Texture
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
                      programStatic: Program,
                      programAnimated: Program,
                      firstPassResult: FirstPassResult) = profiled("Actual draw entities") {

        IndirectDrawDescription(renderState, renderState.renderBatchesStatic, programStatic, commandOrganizationStatic, renderState.vertexIndexBufferStatic, this::beforeDrawStatic, engine.config.debug.isDrawLines, renderState.camera).draw()
        IndirectDrawDescription(renderState, renderState.renderBatchesAnimated, programAnimated, commandOrganizationAnimated, renderState.vertexIndexBufferAnimated, this::beforeDrawAnimated, engine.config.debug.isDrawLines, renderState.camera).draw()

        firstPassResult.verticesDrawn += verticesCount
        firstPassResult.entitiesDrawn += entitiesCount
    }

    override fun beforeDrawStatic(renderState: RenderState, program: Program, renderCam: Camera) {
        beforeDraw(renderState, program, renderState.vertexIndexBufferStatic.vertexStructArray, renderCam)
    }

    override fun beforeDrawAnimated(renderState: RenderState, program: Program, renderCam: Camera) {
        beforeDraw(renderState, program, renderState.vertexIndexBufferAnimated.animatedVertexStructArray, renderCam)
    }

    fun beforeDraw(renderState: RenderState, program: Program,
                   vertexBuffer: PersistentMappedStructBuffer<*>, renderCam: Camera) {
        engine.gpuContext.cullFace = useBackFaceCulling
        program.use()
        program.setUniforms(renderState, renderCam, engine.config, vertexBuffer)
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

fun IndirectDrawDescription.draw() {
    beforeDraw(renderState, program, drawCam)
    with(commandOrganization) {
        drawCountBuffer.put(0, commandCount)
        profiled("Actually render") {
            program.setUniform("entityIndex", 0)
            program.setUniform("entityBaseIndex", 0)
            program.setUniform("indirect", true)
            program.bindShaderStorageBuffer(3, renderState.entitiesState.entitiesBuffer)
            program.bindShaderStorageBuffer(4, entityOffsetBuffer)
            program.bindShaderStorageBuffer(6, renderState.entitiesState.jointsBuffer)

            vertexIndexBuffer.multiDrawElementsIndirectCount(commandBuffer, drawCountBuffer, 0, commandCount, isDrawLines)
        }
    }
}

fun Program.setUniforms(renderState: RenderState, camera: Camera = renderState.camera,
                        config: Config, vertexBuffer: PersistentMappedStructBuffer<*>) = profiled("setUniforms") {

    val viewMatrixAsBuffer = camera.viewMatrixAsBuffer
    val projectionMatrixAsBuffer = camera.projectionMatrixAsBuffer
    val viewProjectionMatrixAsBuffer = camera.viewProjectionMatrixAsBuffer

    use()
    bindShaderStorageBuffer(1, renderState.materialBuffer)
    bindShaderStorageBuffer(3, renderState.entitiesBuffer)
    bindShaderStorageBuffer(6, renderState.entitiesState.jointsBuffer)
    bindShaderStorageBuffer(7, vertexBuffer)
    setUniform("useRainEffect", config.effects.rainEffect != 0.0f)
    setUniform("rainEffect", config.effects.rainEffect)
    setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer)
    setUniformAsMatrix4("lastViewMatrix", viewMatrixAsBuffer)
    setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer)
    setUniformAsMatrix4("viewProjectionMatrix", viewProjectionMatrixAsBuffer)

    setUniform("eyePosition", camera.getPosition())
    setUniform("near", camera.near)
    setUniform("far", camera.far)
    setUniform("time", renderState.time.toInt())
    setUniform("useParallax", config.quality.isUseParallax)
    setUniform("useSteepParallax", config.quality.isUseSteepParallax)
}

fun Program.setTextureUniforms(maps: Map<SimpleMaterial.MAP, Texture>) {
    for (mapEnumEntry in SimpleMaterial.MAP.values()) {

        if (maps.contains(mapEnumEntry)) {
            val map = maps[mapEnumEntry]!!
            if (map.id > 0) {
                gpuContext.bindTexture(mapEnumEntry.textureSlot, map)
                setUniform(mapEnumEntry.uniformKey, true)
            }
        } else {
            setUniform(mapEnumEntry.uniformKey, false)
        }
    }
}

