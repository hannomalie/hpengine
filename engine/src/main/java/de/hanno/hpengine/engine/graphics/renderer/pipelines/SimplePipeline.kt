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
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.engine.vertexbuffer.drawLinesInstancedIndirectBaseVertex
import de.hanno.hpengine.engine.vertexbuffer.multiDrawElementsIndirectCount
import de.hanno.struct.copyTo
import org.joml.FrustumIntersection

open class SimplePipeline @JvmOverloads constructor(private val engine: EngineContext<OpenGl>,
                                                    private val useFrustumCulling: Boolean = true,
                                                    private val useBackFaceCulling: Boolean = true,
                                                    private val useLineDrawingIfActivated: Boolean = true) : Pipeline {

    private var verticesCount = 0
    private var entitiesCount = 0

    private val useIndirectRendering = engine.config.performance.isIndirectRendering && engine.gpuContext.isSupported(BindlessTextures)

    fun prepare(renderState: RenderState, cullCam: Camera) {
        verticesCount = 0
        entitiesCount = 0
        fun CommandOrganization.prepare(batches: List<RenderBatch>) {
            filteredRenderBatches = batches.filter { !it.shouldBeSkipped(cullCam) }
            commandCount = filteredRenderBatches.size
            addCommands(filteredRenderBatches, commandBuffer, entityOffsetBuffer)
        }

        renderState.commandOrganizationStatic.prepare(renderState.renderBatchesStatic)
        renderState.commandOrganizationAnimated.prepare(renderState.renderBatchesAnimated)
    }
    override fun prepare(renderState: RenderState) {
        prepare(renderState, renderState.camera)
    }

    override fun draw(renderState: RenderState,
                      programStatic: Program,
                      programAnimated: Program,
                      firstPassResult: FirstPassResult) {

        draw(renderState, programStatic, programAnimated, firstPassResult, renderState.camera)
    }

    fun draw(renderState: RenderState,
             programStatic: Program,
             programAnimated: Program,
             firstPassResult: FirstPassResult,
             drawCam: Camera = renderState.camera,
             cullCam: Camera = drawCam) = profiled("Actual draw entities") {
        with(renderState) {
            drawStaticAndAnimated(
                DrawDescription(renderState, programStatic, commandOrganizationStatic, vertexIndexBufferStatic, drawCam),
                DrawDescription(renderState, programAnimated, commandOrganizationAnimated, vertexIndexBufferAnimated, drawCam)
            )

            firstPassResult.verticesDrawn += verticesCount
            firstPassResult.entitiesDrawn += entitiesCount
        }
    }

    protected open fun drawStaticAndAnimated(drawDescriptionStatic: DrawDescription, drawDescriptionAnimated: DrawDescription) {
        if (useIndirectRendering) {
            drawStaticAndAnimatedIndirect(drawDescriptionStatic, drawDescriptionAnimated)
        } else {
            drawStaticAndAnimatedDirect(drawDescriptionStatic, drawDescriptionAnimated)
        }
    }

    private fun drawStaticAndAnimatedIndirect(drawDescriptionStatic: DrawDescription,
                                              drawDescriptionAnimated: DrawDescription) {
        // This can be reused ?? To be checked...
        val drawCountBuffer = drawDescriptionStatic.commandOrganization.drawCountBuffer
        fun DrawDescription.drawIndirect(beforeDrawAction: (RenderState, Program, Camera) -> Unit) {
            beforeDrawAction(renderState, program, drawCam)
            with(commandOrganization) {
                drawCountBuffer.put(0, commandCount)
                profiled("Actually render") {
                    program.setUniform("entityIndex", 0)
                    program.setUniform("entityBaseIndex", 0)
                    program.setUniform("indirect", true)
                    program.bindShaderStorageBuffer(3, renderState.entitiesState.entitiesBuffer)
                    program.bindShaderStorageBuffer(4, entityOffsetBuffer)
                    program.bindShaderStorageBuffer(6, renderState.entitiesState.jointsBuffer)
                    drawIndirect(vertexIndexBuffer, commandBuffer, commandCount, drawCountBuffer)
                }
            }
        }

        drawDescriptionStatic.drawIndirect(::beforeDrawStatic)
        drawDescriptionAnimated.drawIndirect(::beforeDrawAnimated)
    }

    private fun drawStaticAndAnimatedDirect(drawDescriptionStatic: DrawDescription,
                                            drawDescriptionAnimated: DrawDescription) {
        fun DrawDescription.drawDirect(beforeDrawAction: (RenderState, Program, Camera) -> Unit) {
            beforeDrawAction(renderState, program, drawCam)
            program.use()
            var indicesCount = 0
            for (batch in commandOrganization.filteredRenderBatches) {
                program.setTextureUniforms(engine.gpuContext, batch.materialInfo.maps)
                indicesCount += draw(vertexIndexBuffer, batch, program, engine.config.debug.isDrawLines)
            }
        }
        drawDescriptionStatic.drawDirect(::beforeDrawStatic)
        drawDescriptionAnimated.drawDirect(::beforeDrawAnimated)
    }

    protected fun drawIndirect(vertexIndexBuffer: VertexIndexBuffer,
                               commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>,
                               commandCount: Int,
                               drawCountBuffer: AtomicCounterBuffer) {

        if (engine.config.debug.isDrawLines && useLineDrawingIfActivated) {
            engine.gpuContext.disable(GlCap.CULL_FACE)
            vertexIndexBuffer.drawLinesInstancedIndirectBaseVertex(commandBuffer, commandCount)
        } else {
            vertexIndexBuffer.multiDrawElementsIndirectCount(commandBuffer, drawCountBuffer, commandCount)
        }
    }

    private fun addCommands(renderBatches: List<RenderBatch>,
                            commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>,
                            entityOffsetBuffer: PersistentMappedStructBuffer<IntStruct>) {

        entityOffsetBuffer.enlarge(renderBatches.size)
        commandBuffer.enlarge(renderBatches.size)

        for ((index, batch) in renderBatches.withIndex()) {
            batch.drawElementsIndirectCommand.copyTo(commandBuffer[index])
            entityOffsetBuffer[index].value = batch.entityBufferIndex
            verticesCount += batch.vertexCount * batch.instanceCount
            entitiesCount += batch.instanceCount
        }
    }

    fun RenderBatch.shouldBeSkipped(cullCam: Camera): Boolean {
        val intersectAABB = cullCam.frustum.frustumIntersection.intersectAab(meshMinWorld, meshMaxWorld)
        val meshIsInFrustum = intersectAABB == FrustumIntersection.INTERSECT || intersectAABB == FrustumIntersection.INSIDE

        val visibleForCamera = meshIsInFrustum || drawElementsIndirectCommand.primCount > 1 // TODO: Better culling for instances

        val culled = engine.config.debug.isUseCpuFrustumCulling && useFrustumCulling && !visibleForCamera
        val isForward = materialInfo.transparencyType.needsForwardRendering
        return culled || isForward
    }

    open fun beforeDrawStatic(renderState: RenderState, program: Program, renderCam: Camera) {
        beforeDraw(renderState, program, renderState.vertexIndexBufferStatic.vertexStructArray, renderCam)
    }

    open fun beforeDrawAnimated(renderState: RenderState, program: Program, renderCam: Camera) {
        beforeDraw(renderState, program, renderState.vertexIndexBufferAnimated.animatedVertexStructArray, renderCam)
    }

    fun beforeDraw(renderState: RenderState, program: Program,
                   vertexBuffer: PersistentMappedStructBuffer<*>, renderCam: Camera) {
        if (useBackFaceCulling) {
            engine.gpuContext.enable(GlCap.CULL_FACE)
        }
        program.use()
        program.setUniforms(renderState, renderCam,
                engine.config, vertexBuffer)
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

fun Program.setTextureUniforms(gpuContext: GpuContext<OpenGl>,
                               maps: Map<SimpleMaterial.MAP, Texture>) {
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

