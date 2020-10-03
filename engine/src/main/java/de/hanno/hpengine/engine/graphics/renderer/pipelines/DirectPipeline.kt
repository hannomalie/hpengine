package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.DirectDrawDescription
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.actuallyDraw
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import org.joml.FrustumIntersection

open class DirectPipeline(private val engine: EngineContext) : Pipeline {

    private var verticesCount = 0
    private var entitiesCount = 0
    private var filteredRenderBatchesStatic = emptyList<RenderBatch>()
    private var filteredRenderBatchesAnimated = emptyList<RenderBatch>()

    override fun prepare(renderState: RenderState) = prepare(renderState, renderState.camera)

    fun prepare(renderState: RenderState, camera: Camera) {
        verticesCount = 0
        entitiesCount = 0

        filteredRenderBatchesStatic = renderState.renderBatchesStatic.filter { !it.shouldBeSkipped(camera) }
        filteredRenderBatchesAnimated = renderState.renderBatchesAnimated.filter { !it.shouldBeSkipped(camera) }
    }

    override fun draw(renderState: RenderState,
                      programStatic: Program,
                      programAnimated: Program,
                      firstPassResult: FirstPassResult) = profiled("Actual draw entities") {

        val drawDescriptionStatic = DirectDrawDescription(renderState, filteredRenderBatchesStatic, programStatic, renderState.vertexIndexBufferStatic, this::beforeDrawStatic, engine.config.debug.isDrawLines, renderState.camera)
        drawDescriptionStatic.draw(engine.gpuContext)

        val drawDescriptionAnimated = DirectDrawDescription(renderState, filteredRenderBatchesAnimated, programAnimated, renderState.vertexIndexBufferAnimated, this::beforeDrawAnimated, engine.config.debug.isDrawLines, renderState.camera)
        drawDescriptionAnimated.draw(engine.gpuContext)

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
                   vertexStructArray: PersistentMappedStructBuffer<*>,
                   renderCam: Camera) {
        engine.gpuContext.cullFace = !engine.config.debug.isDrawLines
        program.use()
        program.setUniforms(renderState, renderCam, engine.config, vertexStructArray)
    }

}

fun DirectDrawDescription.draw(gpuContext: GpuContext<OpenGl>) {
    beforeDraw(renderState, program, drawCam)
    vertexIndexBuffer.indexBuffer.bind()
    for (batch in renderBatches.filter { !it.hasOwnProgram }) {
        gpuContext.cullFace = batch.materialInfo.cullBackFaces
        program.setTextureUniforms(batch.materialInfo.maps)
        vertexIndexBuffer.indexBuffer.actuallyDraw(batch.entityBufferIndex, batch.drawElementsIndirectCommand, program, isDrawLines, false)
    }
    for (groupedBatches in renderBatches.filter { it.hasOwnProgram }.groupBy { it.program }) {
        val firstBatch = groupedBatches.value.first()
        val program = firstBatch.program!!
        vertexIndexBuffer.indexBuffer.bind()

        beforeDraw(renderState, program, drawCam)
        gpuContext.cullFace = firstBatch.materialInfo.cullBackFaces
        program.setTextureUniforms(firstBatch.materialInfo.maps)

        for(batch in groupedBatches.value) {
            vertexIndexBuffer.indexBuffer.actuallyDraw(batch.entityBufferIndex, batch.drawElementsIndirectCommand, program, isDrawLines, false)
        }
    }
}

fun RenderBatch.shouldBeSkipped(cullCam: Camera): Boolean {
    val intersectAABB = cullCam.frustum.frustumIntersection.intersectAab(meshMinWorld, meshMaxWorld)
    val meshIsInFrustum = intersectAABB == FrustumIntersection.INTERSECT || intersectAABB == FrustumIntersection.INSIDE

    val visibleForCamera = meshIsInFrustum || drawElementsIndirectCommand.primCount > 1 // TODO: Better culling for instances

    val culled = !visibleForCamera
    val isForward = materialInfo.transparencyType.needsForwardRendering
    return culled || isForward
}
