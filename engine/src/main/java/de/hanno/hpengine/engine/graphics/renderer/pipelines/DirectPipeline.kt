package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.DrawDescription
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import org.joml.FrustumIntersection

open class DirectPipeline(private val engine: EngineContext<OpenGl>) : Pipeline {

    private var verticesCount = 0
    private var entitiesCount = 0
    private var filteredRenderBatchesStatic: List<RenderBatch> = emptyList()
    private var filteredRenderBatchesAnimated: List<RenderBatch> = emptyList()

    override fun prepare(renderState: RenderState) {
        prepare(renderState, renderState.camera)
    }

    fun prepare(renderState: RenderState, camera: Camera) {
        verticesCount = 0
        entitiesCount = 0
        filteredRenderBatchesStatic = renderState.renderBatchesStatic.filter { !it.shouldBeSkipped(camera) }
        filteredRenderBatchesAnimated = renderState.renderBatchesAnimated.filter { !it.shouldBeSkipped(camera) }
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
             drawCam: Camera = renderState.camera) = profiled("Actual draw entities") {
        with(renderState) {
            drawStaticAndAnimatedDirect(
                DrawDescription(renderState, programStatic, commandOrganizationStatic, vertexIndexBufferStatic, drawCam),
                DrawDescription(renderState, programAnimated, commandOrganizationAnimated, vertexIndexBufferAnimated, drawCam)
            )

            firstPassResult.verticesDrawn += verticesCount
            firstPassResult.entitiesDrawn += entitiesCount
        }
    }

    private fun drawStaticAndAnimatedDirect(drawDescriptionStatic: DrawDescription,
                                            drawDescriptionAnimated: DrawDescription) {
        fun DrawDescription.drawDirect(renderBatches: List<RenderBatch>, beforeDrawAction: (RenderState, Program, Camera) -> Unit) {
            beforeDrawAction(renderState, program, drawCam)
            program.use()
            var indicesCount = 0
            for (batch in renderBatches) {
                program.setTextureUniforms(engine.gpuContext, batch.materialInfo.maps)
                indicesCount += draw(vertexIndexBuffer, batch, program, engine.config.debug.isDrawLines)
            }
        }
        drawDescriptionStatic.drawDirect(filteredRenderBatchesStatic, ::beforeDrawStatic)
        drawDescriptionAnimated.drawDirect(filteredRenderBatchesAnimated, ::beforeDrawAnimated)
    }

    open fun RenderBatch.shouldBeSkipped(cullCam: Camera): Boolean {
        val intersectAABB = cullCam.frustum.frustumIntersection.intersectAab(meshMinWorld, meshMaxWorld)
        val meshIsInFrustum = intersectAABB == FrustumIntersection.INTERSECT || intersectAABB == FrustumIntersection.INSIDE

        val visibleForCamera = meshIsInFrustum || drawElementsIndirectCommand.primCount > 1 // TODO: Better culling for instances

        val culled = !visibleForCamera
        val isForward = materialInfo.transparencyType.needsForwardRendering
        return culled || isForward
    }

    fun beforeDrawStatic(renderState: RenderState, program: Program, renderCam: Camera) {
        beforeDraw(renderState, program, renderState.vertexIndexBufferStatic.vertexStructArray, renderCam)
        customBeforeDrawStatic(renderState, program, renderCam)
    }

    fun beforeDrawAnimated(renderState: RenderState, program: Program, renderCam: Camera) {
        beforeDraw(renderState, program, renderState.vertexIndexBufferAnimated.animatedVertexStructArray, renderCam)
        customBeforeDrawAnimated(renderState, program, renderCam)
    }

    open fun customBeforeDrawStatic(renderState: RenderState, program: Program, renderCam: Camera) { }
    open fun customBeforeDrawAnimated(renderState: RenderState, program: Program, renderCam: Camera) { }

    fun beforeDraw(renderState: RenderState, program: Program,
                   vertexBuffer: PersistentMappedStructBuffer<*>, renderCam: Camera) {
        engine.gpuContext.enable(GlCap.CULL_FACE)
        program.use()
        program.setUniforms(renderState, renderCam,
                engine.config, vertexBuffer)
    }

}

