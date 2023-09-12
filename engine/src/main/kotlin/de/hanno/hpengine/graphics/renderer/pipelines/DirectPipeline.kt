package de.hanno.hpengine.graphics.renderer.pipelines


import de.hanno.hpengine.graphics.constants.PrimitiveType
import de.hanno.hpengine.artemis.model.EntitiesStateHolder
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.DirectDrawDescription
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.constants.RenderingMode.Fill
import de.hanno.hpengine.graphics.constants.RenderingMode.Lines
import de.hanno.hpengine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.state.EntitiesState
import de.hanno.hpengine.graphics.texture.Texture
import de.hanno.hpengine.scene.VertexIndexBuffer
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.joml.FrustumIntersection

open class DirectFirstPassPipeline(
    private val graphicsApi: GraphicsApi,
    private val config: Config,
    private val program: Program<out FirstPassUniforms>,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val fallbackTexture: Texture? = null,
    // TODO: Use shouldBeSkipped
    protected val shouldBeSkipped: RenderBatch.(Camera) -> Boolean = { cullCam: Camera ->
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

    open fun RenderState.extractRenderBatches(): List<RenderBatch> = this[entitiesStateHolder.entitiesState].renderBatchesStatic.filterNot {
        it.shouldBeSkipped(this[primaryCameraStateHolder.camera])
    }
    open fun RenderState.selectVertexIndexBuffer(): VertexIndexBuffer<*> = this[entitiesStateHolder.entitiesState].vertexIndexBufferStatic

    fun draw(renderState: RenderState): Unit = graphicsApi.run {
        profiled("Actual draw entities") {

            val mode = if (config.debug.isDrawLines) Lines else Fill
            val vertexIndexBuffer = renderState.selectVertexIndexBuffer()

            vertexIndexBuffer.indexBuffer.bind()

            val entitiesState = renderState[entitiesStateHolder.entitiesState]
            val camera = renderState[primaryCameraStateHolder.camera]

            drawCustomProgramBatches(camera, entitiesState, renderState, vertexIndexBuffer, mode)
            drawDefaultProgramBatches(camera, entitiesState, renderState, vertexIndexBuffer, mode)
        }
    }

    private fun drawDefaultProgramBatches(
        camera: Camera,
        entitiesState: EntitiesState,
        renderState: RenderState,
        vertexIndexBuffer: VertexIndexBuffer<*>,
        mode: RenderingMode
    ) = graphicsApi.run {
        program.use()
        val viewMatrixAsBuffer = camera.viewMatrixAsBuffer
        val projectionMatrixAsBuffer = camera.projectionMatrixAsBuffer
        val viewProjectionMatrixAsBuffer = camera.viewProjectionMatrixAsBuffer
        program.useAndBind { uniforms: FirstPassUniforms ->
            uniforms.apply {
                materials = entitiesState.materialBuffer
                entities = entitiesState.entitiesBuffer
                uniforms.indirect = false
                when (val uniforms = uniforms) {
                    is StaticFirstPassUniforms -> uniforms.vertices =
                        entitiesState.vertexIndexBufferStatic.vertexStructArray

                    is AnimatedFirstPassUniforms -> {
                        uniforms.joints = entitiesState.jointsBuffer
                        uniforms.vertices = entitiesState.vertexIndexBufferAnimated.vertexStructArray
                    }
                }
                useRainEffect = config.effects.rainEffect != 0.0f
                rainEffect = config.effects.rainEffect
                viewMatrix = viewMatrixAsBuffer
                lastViewMatrix = viewMatrixAsBuffer
                projectionMatrix = projectionMatrixAsBuffer
                viewProjectionMatrix = viewProjectionMatrixAsBuffer

                eyePosition = camera.getPosition()
                near = camera.near
                far = camera.far
                time = renderState.time.toInt()
                useParallax = config.quality.isUseParallax
                useSteepParallax = config.quality.isUseSteepParallax
            }
        }
        program.uniforms.entityBaseIndex = 0
        program.uniforms.indirect = false

        val batchesWithPipelineProgram = renderBatches.filter { !it.hasOwnProgram }.sortedBy { it.material.renderPriority }
        for (batch in batchesWithPipelineProgram) {
            depthMask = batch.material.writesDepth
            cullFace = batch.material.cullBackFaces
            depthTest = batch.material.depthTest
            program.setTextureUniforms(batch.material.maps, fallbackTexture)
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
    }

    private fun drawCustomProgramBatches(
        camera: Camera,
        entitiesState: EntitiesState,
        renderState: RenderState,
        vertexIndexBuffer: VertexIndexBuffer<*>,
        mode: RenderingMode
    ) = graphicsApi.run {
        val batchesWithOwnProgram: Map<Material, List<RenderBatch>> = renderBatches.filter { it.hasOwnProgram }.groupBy { it.material }

        for (groupedBatches in batchesWithOwnProgram) {
            for (batch in groupedBatches.value.sortedBy { it.material.renderPriority }) {
                val program = batch.program!!
                cullFace = batch.material.cullBackFaces
                depthTest = batch.material.depthTest
                depthMask = batch.material.writesDepth

                program.use()
                val viewMatrixAsBuffer = camera.viewMatrixAsBuffer
                val projectionMatrixAsBuffer = camera.projectionMatrixAsBuffer
                val viewProjectionMatrixAsBuffer = camera.viewProjectionMatrixAsBuffer
                program.useAndBind { uniforms ->
                    uniforms.apply {
                        materials = entitiesState.materialBuffer
                        entities = entitiesState.entitiesBuffer
                        program.uniforms.indirect = false
                        when (val uniforms = program.uniforms) {
                            is StaticFirstPassUniforms -> uniforms.vertices =
                                entitiesState.vertexIndexBufferStatic.vertexStructArray

                            is AnimatedFirstPassUniforms -> {
                                uniforms.joints = entitiesState.jointsBuffer
                                uniforms.vertices = entitiesState.vertexIndexBufferAnimated.vertexStructArray
                            }
                        }
                        useRainEffect = config.effects.rainEffect != 0.0f
                        rainEffect = config.effects.rainEffect
                        viewMatrix = viewMatrixAsBuffer
                        lastViewMatrix = viewMatrixAsBuffer
                        projectionMatrix = projectionMatrixAsBuffer
                        viewProjectionMatrix = viewProjectionMatrixAsBuffer

                        eyePosition = camera.getPosition()
                        near = camera.near
                        far = camera.far
                        time = renderState.time.toInt()
                        useParallax = config.quality.isUseParallax
                        useSteepParallax = config.quality.isUseSteepParallax
                    }
                }
                program.uniforms.entityIndex = batch.entityBufferIndex
                program.uniforms.entityBaseIndex = 0
                program.setTextureUniforms(batch.material.maps, fallbackTexture)

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
    }
}

private val <T: Uniforms> Program<T>.tesselationControlShader: TesselationControlShader?
    get() = shaders.firstIsInstanceOrNull<TesselationControlShader>()

val ProgramImpl<*>.primitiveType
    get() = if (tesselationControlShader != null) {
        PrimitiveType.Patches
    } else {
        PrimitiveType.Triangles
    }

context(GraphicsApi)
fun DirectDrawDescription<FirstPassUniforms>.draw() {
    beforeDraw(renderState, program, drawCam)
    if(ignoreCustomPrograms) {
        program.use()
    }

    val batchesWithOwnProgram: Map<Material, List<RenderBatch>> = renderBatches.filter { it.hasOwnProgram }.groupBy { it.material }
    vertexIndexBuffer.indexBuffer.bind()
    for (groupedBatches in batchesWithOwnProgram) {
        var program: Program<FirstPassUniforms> // TODO: Assign this program in the loop below and use() only on change
        for(batch in groupedBatches.value.sortedBy { it.material.renderPriority }) {
            if (!ignoreCustomPrograms) {
                program = (batch.program ?: this.program)
                program.use()
            } else {
                program = this.program
            }
            program.uniforms.entityIndex = batch.entityBufferIndex
            beforeDraw(renderState, program, drawCam)
            cullFace = batch.material.cullBackFaces
            depthTest = batch.material.depthTest
            depthMask = batch.material.writesDepth
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
        depthMask = batch.material.writesDepth
        cullFace = batch.material.cullBackFaces
        depthTest = batch.material.depthTest
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

    depthMask = true // TODO: Resetting defaults here should not be necessary
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
