package de.hanno.hpengine.graphics.vct

import Vector4fStruktImpl.Companion.type
import VoxelGridImpl.Companion.type
import com.artemis.World
import de.hanno.hpengine.Transform
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.Access
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.vertex.QuadVertexBuffer
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.constants.Capability.*
import de.hanno.hpengine.graphics.constants.TextureTarget.TEXTURE_2D
import de.hanno.hpengine.graphics.constants.TextureTarget.TEXTURE_3D
import de.hanno.hpengine.graphics.light.directional.DirectionalLightStateHolder
import de.hanno.hpengine.graphics.light.gi.GIVolumeGrids
import de.hanno.hpengine.graphics.light.gi.GiVolumeStateHolder
import de.hanno.hpengine.graphics.light.gi.gridTextureFormatSized
import de.hanno.hpengine.graphics.light.gi.indexGridTextureFormatSized
import de.hanno.hpengine.graphics.light.point.PointLightStateHolder
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.deferred.extensions.BvHPointLightSecondPassExtension
import de.hanno.hpengine.graphics.renderer.forward.AnimatedDefaultUniforms
import de.hanno.hpengine.graphics.renderer.forward.StaticDefaultUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.*
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.Update
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.skybox.SkyBoxStateHolder
import org.joml.Vector3f
import org.koin.core.annotation.Single
import org.lwjgl.BufferUtils
import struktgen.api.forEachIndexed
import struktgen.api.forIndex
import struktgen.api.get
import struktgen.api.size
import kotlin.math.max

@Single(binds = [VoxelConeTracingExtension::class, DeferredRenderExtension::class])
class VoxelConeTracingExtension(
    private val graphicsApi: GraphicsApi,
    private val renderStateContext: RenderStateContext,
    private val config: Config,
    programManager: ProgramManager,
    private val pointLightExtension: BvHPointLightSecondPassExtension,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val pointLightStateHolder: PointLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val entityBuffer: EntityBuffer,
    private val skyBoxStateHolder: SkyBoxStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val giVolumeStateHolder: GiVolumeStateHolder,
    private val defaultBatchesSystem: DefaultBatchesSystem,
    private val materialSystem: MaterialSystem,
) : DeferredRenderExtension {

    private val fullscreenBuffer = QuadVertexBuffer(graphicsApi)
    private val lineVertices = graphicsApi.PersistentShaderStorageBuffer(100 * Vector4fStrukt.type.sizeInBytes).typed(Vector4fStrukt.type)
    val voxelGrids = renderStateContext.renderState.registerState {
        //PersistentShaderStorageBuffer(VoxelGrid.type.sizeInBytes).typed(VoxelGrid.type)
        graphicsApi.PersistentShaderStorageBuffer(Byte.SIZE_BYTES).typed(VoxelGrid.type) // make it one byte size as long as no elements, so that we can encode the "empty" state
    }

    private val voxelizerStatic = programManager.getProgram(
        config.EngineAsset("shaders/voxelize_vertex.glsl").toCodeSource(),
        config.EngineAsset("shaders/voxelize_fragment.glsl").toCodeSource(),
        config.EngineAsset("shaders/voxelize_geometry.glsl").toCodeSource(),
        Defines(),
        VoxelizerUniformsStatic(graphicsApi)
    )

    private val voxelizerAnimated = programManager.getProgram(
        config.EngineAsset("shaders/voxelize_vertex.glsl").toCodeSource(),
        config.EngineAsset("shaders/voxelize_fragment.glsl").toCodeSource(),
        config.EngineAsset("shaders/voxelize_geometry.glsl").toCodeSource(),
        Defines(),
        VoxelizerUniformsStatic(graphicsApi)
    )

    private val voxelConeTraceProgram = programManager.getProgram(
        config.EngineAsset("shaders/passthrough_vertex.glsl").toCodeSource(),
        config.EngineAsset("shaders/voxel_cone_trace_fragment.glsl").toCodeSource(),
        null,
        Defines(),
        Uniforms.Empty
    )

    private val texture3DMipMapAlphaBlendComputeProgram = programManager.getComputeProgram(config.EngineAsset("shaders/texture3D_mipmap_alphablend_compute.glsl"))
    private val texture3DMipMapComputeProgram = programManager.getComputeProgram(config.EngineAsset("shaders/texture3D_mipmap_compute.glsl"))
    private val clearDynamicVoxelsComputeProgram = programManager.getComputeProgram(config.EngineAsset("shaders/texture3D_clear_dynamic_voxels_compute.glsl"))
    private val injectLightComputeProgram = programManager.getComputeProgram(config.EngineAsset("shaders/texture3D_inject_light_compute.glsl"))
    private val injectMultipleBounceLightComputeProgram = programManager.getComputeProgram(config.EngineAsset("shaders/texture3D_inject_bounce_light_compute.glsl"))

    private var lightInjectedFramesAgo: Int = 0

    private val staticPipeline = DirectPipeline(graphicsApi, config, voxelizerStatic, entitiesStateHolder, entityBuffer, primaryCameraStateHolder, defaultBatchesSystem, materialSystem)
    private val animatedPipeline = DirectPipeline(graphicsApi, config, voxelizerAnimated, entitiesStateHolder, entityBuffer, primaryCameraStateHolder, defaultBatchesSystem, materialSystem)
    private val useIndirectDrawing = false

    private var litInCycle: Long = -1
    private val entityVoxelizedInCycle = mutableMapOf<String, Long>()

    private var sceneInitiallyDrawn = false
    private var voxelizeDynamicEntites = false

    private var gridCache = mutableMapOf<Int, GIVolumeGrids>()

    override fun renderFirstPass(renderState: RenderState) = graphicsApi.run {
        profiled("VCT first pass") {
            val noGridsInUse = renderState[this@VoxelConeTracingExtension.voxelGrids].sizeInBytes == Byte.SIZE_BYTES
            if(noGridsInUse) return

            val directionalLightMoved = renderState[directionalLightStateHolder.directionalLightHasMovedInCycle].underlying > litInCycle
            val anyPointLightHasMoved = renderState[pointLightStateHolder.lightState].pointLightMovedInCycle.entries.any { litInCycle <= it.value }
            val bounces = 1

            val entitiesToVoxelize = if(!sceneInitiallyDrawn || config.debug.isForceRevoxelization) {
                renderState[defaultBatchesSystem.renderBatchesStatic]
            } else {
                renderState[defaultBatchesSystem.renderBatchesStatic].filter { batch ->
                    val entityVoxelizationCycle = entityVoxelizedInCycle[batch.entityName]
                    val voxelizeBecauseItIsDynamic = voxelizeDynamicEntites && batch.update == Update.DYNAMIC
                    val preventVoxelization = !batch.contributesToGi || if(voxelizeDynamicEntites) false else batch.update == Update.DYNAMIC
                    if(entityVoxelizationCycle == null) {
                        !preventVoxelization
                    } else {
                        batch.movedInCycle > entityVoxelizationCycle && voxelizeBecauseItIsDynamic
                    }
                }
            }

            val needsRevoxelization = entitiesToVoxelize.isNotEmpty()
            voxelizeScene(renderState, entitiesToVoxelize)


            if ((config.performance.updateGiOnSceneChange || config.debug.isForceRevoxelization) && (needsRevoxelization || directionalLightMoved || anyPointLightHasMoved)) {
                lightInjectedFramesAgo = 0
            }
            val needsLightInjection = lightInjectedFramesAgo < bounces


            injectLight(renderState, bounces, needsLightInjection)
            config.debug.isForceRevoxelization = false
        }
    }

    fun voxelizeScene(renderState: RenderState, batches: List<RenderBatch>) = graphicsApi.run {
        if(batches.isEmpty()) return
        println("Voxelizing....")

        val voxelGrids = renderState[voxelGrids]
        val maxGridCount = voxelGrids.typedBuffer.size
        voxelGrids.typedBuffer.forEachIndexed(maxGridCount) { voxelGridIndex, currentVoxelGrid ->
            profiled("Clear voxels") {
                if (config.debug.isForceRevoxelization || !sceneInitiallyDrawn) {
                    if(currentVoxelGrid.grid != 0 &&
                        currentVoxelGrid.indexGrid != 0 &&
                        currentVoxelGrid.normalGrid != 0 &&
                        currentVoxelGrid.albedoGrid != 0
                    ) {
                        clearTexImage(currentVoxelGrid.grid, gridTextureFormat, 0, TexelComponentType.Float)
                        clearTexImage(currentVoxelGrid.indexGrid, indexGridTextureFormat, 0, TexelComponentType.UnsignedInt)
                        clearTexImage(currentVoxelGrid.normalGrid, gridTextureFormat, 0, TexelComponentType.Float)
                        clearTexImage(currentVoxelGrid.albedoGrid, gridTextureFormat, 0, TexelComponentType.Float)
                    }
                } else {
                    graphicsApi.run { clearDynamicVoxelsComputeProgram.use() }
                    val num_groups_xyz = Math.max(currentVoxelGrid.gridSize / 8, 1)
                    bindImageTexture(0, currentVoxelGrid.albedoGrid, 0, true, 0, Access.WriteOnly, gridTextureFormatSized)
                    bindImageTexture(1, currentVoxelGrid.normalGrid, 0, true, 0, Access.ReadWrite, gridTextureFormatSized)
                    bindImageTexture(3, currentVoxelGrid.currentVoxelTarget, 0, true, 0, Access.WriteOnly, gridTextureFormatSized)
                    clearDynamicVoxelsComputeProgram.bindShaderStorageBuffer(5, voxelGrids)
                    clearDynamicVoxelsComputeProgram.setUniform("voxelGridIndex", voxelGridIndex)
                    clearDynamicVoxelsComputeProgram.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz)
                }
            }

            profiled("Voxelization") {
                viewPort(0, 0, currentVoxelGrid.gridSize, currentVoxelGrid.gridSize)
                voxelizerStatic.use()
                bindImageTexture(3, currentVoxelGrid.normalGrid, 0, true, 0, Access.WriteOnly, gridTextureFormatSized)
                bindImageTexture(5, currentVoxelGrid.albedoGrid, 0, true, 0, Access.WriteOnly, gridTextureFormatSized)
                bindImageTexture(6, currentVoxelGrid.indexGrid, 0, true, 0, Access.WriteOnly, indexGridTextureFormatSized)

                voxelizerStatic.setUniform("voxelGridIndex", voxelGridIndex)
                voxelizerStatic.setUniform("voxelGridCount", voxelGrids.typedBuffer.size)
                voxelizerStatic.bindShaderStorageBuffer(1, renderState[materialSystem.materialBuffer])
                voxelizerStatic.bindShaderStorageBuffer(3, renderState[entityBuffer.entitiesBuffer])
                voxelizerStatic.bindShaderStorageBuffer(5, voxelGrids)
                voxelizerStatic.bindShaderStorageBuffer(7, renderState[entitiesStateHolder.entitiesState].vertexIndexBufferStatic.vertexStructArray)

                voxelizerStatic.setUniform("writeVoxels", true)
                depthMask = false
                disable(DEPTH_TEST)
                disable(BLEND)
                disable(CULL_FACE)
                colorMask(red = false, green = false, blue = false, alpha = false)

                renderState[entitiesStateHolder.entitiesState].vertexIndexBufferStatic.indexBuffer.bind()
                for (entity in batches) {
                    voxelizerStatic.setTextureUniforms(graphicsApi, entity.material.maps)
                    renderState[entitiesStateHolder.entitiesState].vertexIndexBufferStatic.indexBuffer.draw(
                        entity.drawElementsIndirectCommand,
                        bindIndexBuffer = false,
                        primitiveType = PrimitiveType.Triangles,
                        mode = RenderingMode.Fill
                    )
                }

                if(config.debug.isDebugVoxels) {
                    memoryBarrier()
                    mipmapGrid(currentVoxelGrid.currentVoxelSource, texture3DMipMapAlphaBlendComputeProgram, renderState)
                }
            }
        }

        for (entity in batches) {
            entityVoxelizedInCycle[entity.entityName] = renderState.cycle
        }
        sceneInitiallyDrawn = true
    }

    fun injectLight(renderState: RenderState, bounces: Int, needsLightInjection: Boolean) = graphicsApi.run {
        if (needsLightInjection) {
            val voxelGrids = renderState[this@VoxelConeTracingExtension.voxelGrids]
            litInCycle = renderState.cycle
            profiled("grid shading") {
//                val maxGridCount = if(engine.gpuContext.isSupported(BindlessTextures)) voxelGrids.size else 1
                val maxGridCount = voxelGrids.typedBuffer.size
                voxelGrids.typedBuffer.forEachIndexed(maxGridCount) { voxelGridIndex, currentVoxelGrid ->

                    val numGroupsXyz = max(currentVoxelGrid.gridSize / 8, 1)

                    if(lightInjectedFramesAgo == 0) {
                        with(injectLightComputeProgram) {
                            use()
                            bindTexture(1, TEXTURE_3D, currentVoxelGrid.albedoGrid)
                            bindTexture(2, TEXTURE_3D, currentVoxelGrid.normalGrid)
                            bindTexture(3, TEXTURE_3D, currentVoxelGrid.indexGrid)
                            val directionalLightState = renderState[directionalLightStateHolder.lightState]
                            if(directionalLightState.typedBuffer.size > 0) {
                                bindTexture(6, TEXTURE_2D, directionalLightState.typedBuffer.forIndex(0) { it.shadowMapId })
                            }
                            bindImageTexture(0, currentVoxelGrid.grid, 0, true, 0, Access.WriteOnly, gridTextureFormatSized)
                            setUniform("pointLightCount", renderState[pointLightStateHolder.lightState].pointLightCount)
                            bindShaderStorageBuffer(1, renderState[materialSystem.materialBuffer])
                            bindShaderStorageBuffer(2, renderState[pointLightStateHolder.lightState].pointLightBuffer)
                            bindShaderStorageBuffer(3, directionalLightState)
                            bindShaderStorageBuffer(4, renderState[entityBuffer.entitiesBuffer])
                            bindShaderStorageBuffer(5, voxelGrids)
                            bindShaderStorageBuffer(6, pointLightExtension.bvh)
                            setUniform("nodeCount", pointLightExtension.nodeCount)
                            setUniform("bounces", bounces)
                            setUniform("voxelGridIndex", voxelGridIndex)
                            setUniform("voxelGridCount", voxelGrids.typedBuffer.size)

                            dispatchCompute(numGroupsXyz, numGroupsXyz, numGroupsXyz)
                            mipmapGrid(currentVoxelGrid.grid, renderState = renderState)
                        }
                    }
//                    if(bounces > 1 && lightInjectedFramesAgo == 0) {//> 0) {
//                        with(injectMultipleBounceLightComputeProgram) {
//                            use()
//                            GL42.glBindImageTexture(0, currentVoxelGrid.grid, 0, false, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
//                            setUniform("bounces", bounces)
//                            setUniform("lightInjectedFramesAgo", lightInjectedFramesAgo)
//                            setUniform("voxelGridIndex", voxelGridIndex)
//                            bindShaderStorageBuffer(5, renderState.get(voxelGridBufferRef).voxelGridBuffer)
//                            dispatchCompute(numGroupsXyz, numGroupsXyz, numGroupsXyz)
//                            mipmapGrid(currentVoxelGrid.grid)
//                        }
//                    }
                }

                memoryBarrier()
                lightInjectedFramesAgo++
            }
        }
        colorMask(red = true, green = true, blue = true, alpha = true)
    }

    override fun update(deltaSeconds: Float) = staticPipeline.prepare(renderStateContext.renderState.currentWriteState)

    private fun mipmapGrid(textureId: Int, renderState: RenderState) = graphicsApi.run {
        profiled("grid mipmap") {
            graphicsApi.memoryBarrier()
            mipmapGrid(textureId, texture3DMipMapAlphaBlendComputeProgram, renderState)
            graphicsApi.memoryBarrier()
        }
    }

    private fun mipmapGrid(texture3D: Int, shader: ComputeProgram<out Uniforms>, renderState: RenderState) = graphicsApi.run {
        shader.use()
        val voxelGrids = renderState[this@VoxelConeTracingExtension.voxelGrids]
        val globalGrid = voxelGrids.typedBuffer[0]
        val size = voxelGrids.typedBuffer.byteBuffer.run { globalGrid.gridSize }
        var currentSizeSource = 2 * size
        var currentMipMapLevel = 0

        while (currentSizeSource > 1) {
            currentSizeSource /= 2
            val currentSizeTarget = currentSizeSource / 2
            currentMipMapLevel++

            bindImageTexture(0, texture3D, currentMipMapLevel - 1, true, 0, Access.ReadOnly, gridTextureFormatSized)
            bindImageTexture(1, texture3D, currentMipMapLevel, true, 0, Access.WriteOnly, gridTextureFormatSized)
            bindImageTexture(3, voxelGrids.typedBuffer.byteBuffer.run { globalGrid.normalGrid }, currentMipMapLevel - 1, true, 0, Access.ReadOnly, gridTextureFormatSized)
            shader.setUniform("sourceSize", currentSizeSource)
            shader.setUniform("targetSize", currentSizeTarget)

            val num_groups_xyz = Math.max(currentSizeTarget / 8, 1)
            shader.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz)
        }
    }

    override fun renderSecondPassHalfScreen(renderState: RenderState) = graphicsApi.run {
        depthTest = false
        val voxelGrids = renderState[this@VoxelConeTracingExtension.voxelGrids]
        profiled("VCT second pass") {

//            engine.gpuContext.blend = true
//            engine.gpuContext.blendEquation = BlendMode.FUNC_ADD
//                val maxGridCount = if(engine.gpuContext.isSupported(BindlessTextures)) voxelGrids.size else 1
            val maxGridCount = voxelGrids.typedBuffer.size
            voxelGrids.typedBuffer.forEachIndexed(maxGridCount) { voxelGridIndex, currentVoxelGrid ->
                // Just for safety, if voxelgrid data is not initialized, to not freeze your pc
                if(currentVoxelGrid.scale > 0.1f) {
                    bindTexture(0, TEXTURE_2D, deferredRenderingBuffer.positionMap)
                    bindTexture(1, TEXTURE_2D, deferredRenderingBuffer.normalMap)
                    bindTexture(2, TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
                    bindTexture(3, TEXTURE_2D, deferredRenderingBuffer.motionMap)
                    bindTexture(7, TEXTURE_2D, deferredRenderingBuffer.visibilityMap)
                    bindTexture(9, TEXTURE_3D, currentVoxelGrid.grid)
                    bindTexture(10, TEXTURE_3D, currentVoxelGrid.indexGrid)
                    bindTexture(11, TEXTURE_2D, deferredRenderingBuffer.ambientOcclusionScatteringMap)
                    bindTexture(12, TEXTURE_3D, currentVoxelGrid.albedoGrid)
                    bindTexture(13, TEXTURE_3D, currentVoxelGrid.normalGrid)

                    val camera = renderState[primaryCameraStateHolder.camera]

                    voxelConeTraceProgram.use()
                    val camTranslation = Vector3f()
                    voxelConeTraceProgram.setUniform("voxelGridIndex", voxelGridIndex)
                    voxelConeTraceProgram.setUniform("eyePosition", camera.transform.getTranslation(camTranslation))
                    voxelConeTraceProgram.setUniformAsMatrix4("viewMatrix", camera.viewMatrixBuffer)
                    voxelConeTraceProgram.setUniformAsMatrix4("projectionMatrix", camera.projectionMatrixBuffer)
                    voxelConeTraceProgram.bindShaderStorageBuffer(0, deferredRenderingBuffer.exposureBuffer)
                    voxelConeTraceProgram.bindShaderStorageBuffer(5, voxelGrids)
                    voxelConeTraceProgram.setUniform("voxelGridCount", voxelGrids.typedBuffer.size)
                    voxelConeTraceProgram.setUniform("useAmbientOcclusion", config.quality.isUseAmbientOcclusion)
                    voxelConeTraceProgram.setUniform("screenWidth", config.width.toFloat() / 2f)
                    voxelConeTraceProgram.setUniform("screenHeight", config.height.toFloat() / 2f)
                    voxelConeTraceProgram.setUniform("skyBoxMaterialIndex", renderState[skyBoxStateHolder.skyBoxMaterialIndex])
                    voxelConeTraceProgram.setUniform("debugVoxels", config.debug.isDebugVoxels)
                    fullscreenBuffer.draw(indexBuffer = null)
                    //        boolean entityOrDirectionalLightHasMoved = renderState.entityMovedInCycle || renderState.directionalLightNeedsShadowMapRender;
                    //        if(entityOrDirectionalLightHasMoved)
                    //        {
                    //            if only second bounce, clear current target texture
                    //            ARBClearTexture.glClearTexImage(currentVoxelSource, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER);
                    //        }
                }
            }
        }
        depthTest = true
    }

    override fun extract(renderState: RenderState, world: World) {
        extract(renderState)
    }
    fun extract(renderState: RenderState) {
        val list = renderState[giVolumeStateHolder.giVolumesState].volumes
        if(list.isEmpty()) return

        val targetSize = list.size * VoxelGrid.type.sizeInBytes
        renderState[voxelGrids].ensureCapacityInBytes(targetSize)
//        for (index in renderState[voxelGrids].indices) {
//            val target = renderState[voxelGrids][index]
//            val source = list[index]
//             TODO: Use volume grid cache somehow
//            val giVolumeGrids = source.giVolumeGrids
//            target.grid = giVolumeGrids.grid.id
//            target.gridHandle = giVolumeGrids.grid.handle
//            target.indexGrid = giVolumeGrids.indexGrid.id
//            target.indexGridHandle = giVolumeGrids.indexGrid.handle
//            target.albedoGrid = giVolumeGrids.albedoGrid.id
//            target.albedoGridHandle = giVolumeGrids.albedoGrid.handle
//            target.normalGrid = giVolumeGrids.normalGrid.id
//            target.normalGridHandle = giVolumeGrids.normalGrid.handle
//            target.gridSize = source.giVolumeGrids.gridSize
//            target.position.set(source.entity.transform.position)
//            target.scale = source.scale
//            target.projectionMatrix.set(source.orthoCam.projectionMatrix)
//        }
    }

    override fun renderEditor(renderState: RenderState) {
        val grids = renderState[voxelGrids]

//             TODO: Reimplement
//        val linePoints = mutableListOf<Vector3fc>().apply {
//            for(gridIndex in grids.indices) {
//                val grid = grids[gridIndex]
//                addAABBLines(
//                    grid.position.toJoml().sub(Vector3f(grid.gridSizeHalfScaled.toFloat())),
//                    grid.position.toJoml().add(Vector3f(grid.gridSizeHalfScaled.toFloat()))
//                )
//            }
//        }
//        deferredRenderingBuffer.finalBuffer.use(gpuContext, false)
//        gpuContext.blend = false
//
//        drawLines(renderStateManager, programManager, lineVertices, linePoints, color = Vector3f(1f, 0f, 0f))
    }

    companion object {
        val gridTextureFormat = Format.RGBA//GL11.GL_R;
        val indexGridTextureFormat = Format.RED_INTEGER//GL30.GL_R32UI;
    }
}
class VoxelizerUniformsStatic(graphicsApi: GraphicsApi) : StaticDefaultUniforms(graphicsApi) {
    val voxelGridIndex by IntType()
    val voxelGridCount by IntType()
    val voxelGrids by SSBO("VoxelGrid", 5, graphicsApi.PersistentShaderStorageBuffer(VoxelGrid.type.sizeInBytes).typed(VoxelGrid.type))
    val writeVoxels by BooleanType(true)
}
class VoxelizerUniformsAnimated(graphicsApi: GraphicsApi) : AnimatedDefaultUniforms(graphicsApi) {
    val voxelGridIndex by IntType()
    val voxelGridCount by IntType()
    val voxelGrids by SSBO("VoxelGrid", 5, graphicsApi.PersistentShaderStorageBuffer(VoxelGrid.type.sizeInBytes).typed(VoxelGrid.type))
    val writeVoxels by BooleanType(true)
}

var ZERO_BUFFER = BufferUtils.createFloatBuffer(4).apply {
    put(0f)
    put(0f)
    put(0f)
    put(0f)
    rewind()
}
var ZERO_BUFFER_INT = BufferUtils.createIntBuffer(4).apply {
    put(0)
    put(0)
    put(0)
    put(0)
    rewind()
}
var BLUE_BUFFER = BufferUtils.createFloatBuffer(4).apply {
    put(0f)
    put(0f)
    put(1f)
    put(0f)
    rewind()
}
val identityMatrix44Buffer = BufferUtils.createFloatBuffer(16).apply {
    Transform().get(this)
}