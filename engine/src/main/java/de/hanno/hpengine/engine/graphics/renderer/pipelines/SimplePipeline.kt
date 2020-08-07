package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.BindlessTextures
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.DrawDescription
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.vertexbuffer.multiDrawElementsIndirectCount
import de.hanno.struct.copyTo

open class SimplePipeline @JvmOverloads constructor(private val engine: EngineContext<OpenGl>,
                                                    private val useFrustumCulling: Boolean = true,
                                                    private val useBackFaceCulling: Boolean = true,
                                                    private val useLineDrawingIfActivated: Boolean = true) : Pipeline {

    private var verticesCount = 0
    private var entitiesCount = 0
    private var commandOrganizationStatic = CommandOrganization(engine.gpuContext)
    private var commandOrganizationAnimated = CommandOrganization(engine.gpuContext)

    private val useBindlessTextures = engine.gpuContext.isSupported(BindlessTextures)
    private val useIndirectRendering = engine.config.performance.isIndirectRendering

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

        drawStaticAndAnimated(
                DrawDescription(renderState, renderState.renderBatchesStatic, programStatic, commandOrganizationStatic, renderState.vertexIndexBufferStatic, this::beforeDrawStatic, engine.config.debug.isDrawLines, renderState.camera),
                DrawDescription(renderState, renderState.renderBatchesAnimated, programAnimated, commandOrganizationAnimated, renderState.vertexIndexBufferAnimated, this::beforeDrawAnimated, engine.config.debug.isDrawLines, renderState.camera)
        )

        firstPassResult.verticesDrawn += verticesCount
        firstPassResult.entitiesDrawn += entitiesCount
    }

    protected open fun drawStaticAndAnimated(drawDescriptionStatic: DrawDescription, drawDescriptionAnimated: DrawDescription) {
        if (useIndirectRendering && useBindlessTextures) {
            drawDescriptionStatic.drawIndirect()
            drawDescriptionAnimated.drawIndirect()
        } else {
            drawDescriptionStatic.drawDirect()
            drawDescriptionAnimated.drawDirect()
        }
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

    entityOffsetBuffer.enlarge(renderBatches.size)
    commandBuffer.enlarge(renderBatches.size)

    for ((index, batch) in renderBatches.withIndex()) {
        batch.drawElementsIndirectCommand.copyTo(commandBuffer[index])
        entityOffsetBuffer[index].value = batch.entityBufferIndex
    }
}

fun DrawDescription.drawIndirect() {
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
    setUniform("timeGpu", System.currentTimeMillis().toInt())
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

