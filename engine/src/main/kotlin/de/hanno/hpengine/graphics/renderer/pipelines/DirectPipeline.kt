package de.hanno.hpengine.graphics.renderer.pipelines

import de.hanno.hpengine.backend.OpenGl
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.DirectDrawDescription
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.graphics.renderer.drawstrategy.PrimitiveType
import de.hanno.hpengine.graphics.renderer.drawstrategy.RenderingMode.Faces
import de.hanno.hpengine.graphics.renderer.drawstrategy.RenderingMode.Lines
import de.hanno.hpengine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.model.material.Material
import org.joml.FrustumIntersection

open class DirectPipeline(
    private val config: Config,
    private val gpuContext: GpuContext<OpenGl>,
    private val useBackFaceCulling: Boolean = true,
    private val shouldBeSkipped: RenderBatch.(Camera) -> Boolean = { cullCam: Camera ->
        isCulled(cullCam) && isForwardRendered
    }
) : Pipeline {

    private var verticesCount = 0
    private var entitiesCount = 0
    private var filteredRenderBatchesStatic = emptyList<RenderBatch>()
    private var filteredRenderBatchesAnimated = emptyList<RenderBatch>()

    override fun prepare(renderState: RenderState) = prepare(renderState, renderState.camera)

    fun prepare(renderState: RenderState, camera: Camera) {
        if(config.debug.freezeCulling) return
        verticesCount = 0
        entitiesCount = 0

        filteredRenderBatchesStatic = renderState.renderBatchesStatic.filterNot { it.shouldBeSkipped(camera) }
        filteredRenderBatchesAnimated = renderState.renderBatchesAnimated.filterNot { it.shouldBeSkipped(camera) }
    }

    override fun draw(renderState: RenderState,
                      programStatic: Program<StaticFirstPassUniforms>,
                      programAnimated: Program<AnimatedFirstPassUniforms>,
                      firstPassResult: FirstPassResult
    ) = profiled("Actual draw entities") {

        val mode = if (config.debug.isDrawLines) Lines else Faces

        val drawDescriptionStatic = DirectDrawDescription(renderState, filteredRenderBatchesStatic, programStatic, renderState.vertexIndexBufferStatic, this::beforeDrawStatic, mode, renderState.camera, renderState.camera, false)
        drawDescriptionStatic.draw(gpuContext)

        val drawDescriptionAnimated = DirectDrawDescription(renderState, filteredRenderBatchesAnimated, programAnimated, renderState.vertexIndexBufferAnimated, this::beforeDrawAnimated, mode, renderState.camera, renderState.camera, false)
        drawDescriptionAnimated.draw(gpuContext)

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
        gpuContext.cullFace = useBackFaceCulling || !config.debug.isDrawLines
        program.use()
        program.setUniforms(renderState, renderCam, config, false)
        program.uniforms.indirect = false
    }

}

open class DirectFirstPassPipeline(
    private val config: Config,
    private val gpuContext: GpuContext<OpenGl>,
    private val program: Program<out FirstPassUniforms>,
    private val shouldBeSkipped: RenderBatch.(Camera) -> Boolean = { cullCam: Camera ->
        isCulled(cullCam) || isForwardRendered
    }
) {
    private var verticesCount = 0
    private var entitiesCount = 0
    private var renderBatches = emptyList<RenderBatch>()

    fun prepare(renderState: RenderState) {
        if(config.debug.freezeCulling) return
        verticesCount = 0
        entitiesCount = 0

        renderBatches = renderState.extractRenderBatches()
    }

    open fun RenderState.extractRenderBatches(): List<RenderBatch> = renderBatchesStatic.filterNot {
        it.shouldBeSkipped(camera)
    }
    open fun RenderState.selectVertexIndexBuffer() = vertexIndexBufferStatic

    fun draw(renderState: RenderState) = profiled("Actual draw entities") {

        val mode = if (config.debug.isDrawLines) Lines else Faces
        val vertexIndexBuffer = renderState.selectVertexIndexBuffer()

        vertexIndexBuffer.indexBuffer.bind()

        val batchesWithOwnProgram: Map<Material, List<RenderBatch>> = renderBatches.filter { it.hasOwnProgram }.groupBy { it.material }
        for (groupedBatches in batchesWithOwnProgram) {
            for (batch in groupedBatches.value.sortedBy { it.material.renderPriority }) {
                val program = batch.program!!
                gpuContext.cullFace = batch.material.cullBackFaces
                gpuContext.depthTest = batch.material.depthTest
                gpuContext.depthMask = batch.material.writesDepth

                program.use()
                program.setUniforms(renderState, renderState.camera, config, false)
                program.uniforms.entityIndex = batch.entityBufferIndex
                program.uniforms.entityBaseIndex = 0
                program.setTextureUniforms(batch.material.maps)

                program.bind()
                vertexIndexBuffer.indexBuffer.draw(
                    batch.drawElementsIndirectCommand,
                    primitiveType = program.primitiveType,
                    mode = mode,
                    bindIndexBuffer = false,
                )
                verticesCount += batch.vertexCount
                entitiesCount += 1
            }
        }

        program.use()
        program.setUniforms(renderState, renderState.camera, config, false)
        program.uniforms.entityBaseIndex = 0
        program.uniforms.indirect = false

        val batchesWithPipelineProgram = renderBatches.filter { !it.hasOwnProgram }.sortedBy { it.material.renderPriority }
        for (batch in batchesWithPipelineProgram) {
            gpuContext.depthMask = batch.material.writesDepth
            gpuContext.cullFace = batch.material.cullBackFaces
            gpuContext.depthTest = batch.material.depthTest
            program.setTextureUniforms(batch.material.maps)
            program.uniforms.entityIndex = batch.entityBufferIndex
            program.bind()
            vertexIndexBuffer.indexBuffer.draw(
                batch.drawElementsIndirectCommand,
                primitiveType = PrimitiveType.Triangles,
                mode = mode,
                bindIndexBuffer = false,
            )
            verticesCount += batch.vertexCount
            entitiesCount += 1
        }

        gpuContext.depthMask = true // TODO: Resetting defaults here should not be necessary

        renderState.latestDrawResult.firstPassResult.verticesDrawn += verticesCount
        renderState.latestDrawResult.firstPassResult.entitiesDrawn += entitiesCount
    }
}

val Program<*>.primitiveType
    get() = if (tesselationControlShader != null) {
        PrimitiveType.Patches
    } else {
        PrimitiveType.Triangles
    }

fun <T: FirstPassUniforms> DirectDrawDescription<T>.draw(gpuContext: GpuContext<OpenGl>) {
    beforeDraw(renderState, program, drawCam)
    if(ignoreCustomPrograms) {
        program.use()
    }

    val batchesWithOwnProgram: Map<Material, List<RenderBatch>> = renderBatches.filter { it.hasOwnProgram }.groupBy { it.material }
    vertexIndexBuffer.indexBuffer.bind()
    for (groupedBatches in batchesWithOwnProgram) {
        var program: Program<T> // TODO: Assign this program in the loop below and use() only on change
        for(batch in groupedBatches.value.sortedBy { it.material.renderPriority }) {
            if (!ignoreCustomPrograms) {
                program = (batch.program ?: this.program) as Program<T>
                program.use()
            } else {
                program = this.program
            }
            program.uniforms.entityIndex = batch.entityBufferIndex
            beforeDraw(renderState, program, drawCam)
            gpuContext.cullFace = batch.material.cullBackFaces
            gpuContext.depthTest = batch.material.depthTest
            gpuContext.depthMask = batch.material.writesDepth
            program.setTextureUniforms(batch.material.maps)
            val primitiveType = if(program.tesselationControlShader != null) PrimitiveType.Patches else PrimitiveType.Triangles

            program.bind()
            vertexIndexBuffer.indexBuffer.draw(
                batch.drawElementsIndirectCommand,
                primitiveType = primitiveType,
                mode = mode,
                bindIndexBuffer = false,
            )
        }
    }

    beforeDraw(renderState, program, drawCam)
    vertexIndexBuffer.indexBuffer.bind()
    for (batch in renderBatches.filter { !it.hasOwnProgram }.sortedBy { it.material.renderPriority }) {
        gpuContext.depthMask = batch.material.writesDepth
        gpuContext.cullFace = batch.material.cullBackFaces
        gpuContext.depthTest = batch.material.depthTest
        program.setTextureUniforms(batch.material.maps)
        program.uniforms.entityIndex = batch.entityBufferIndex
        program.bind()
        vertexIndexBuffer.indexBuffer.draw(
            batch.drawElementsIndirectCommand,
            primitiveType = PrimitiveType.Triangles,
            mode = mode,
            bindIndexBuffer = false,
        )
    }

    gpuContext.depthMask = true // TODO: Resetting defaults here should not be necessary
}

fun RenderBatch.isCulled(cullCam: Camera): Boolean {
    if (!isVisible) return true

    val intersectAABB = cullCam.frustum.frustumIntersection.intersectAab(meshMinWorld, meshMaxWorld)
    val meshIsInFrustum = intersectAABB == FrustumIntersection.INTERSECT || intersectAABB == FrustumIntersection.INSIDE

    val visibleForCamera =
        meshIsInFrustum || drawElementsIndirectCommand.instanceCount > 1 // TODO: Better culling for instances

    return !visibleForCamera
}

val RenderBatch.isForwardRendered: Boolean get() = material.transparencyType.needsForwardRendering
