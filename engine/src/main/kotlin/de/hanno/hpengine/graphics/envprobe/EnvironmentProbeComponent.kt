package de.hanno.hpengine.graphics.envprobe

import InternalTextureFormat
import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.Component
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.constants.MinFilter
import de.hanno.hpengine.graphics.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.constants.WrapMode
import de.hanno.hpengine.graphics.renderer.forward.StaticDefaultUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.DirectPipeline
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.state.StateRef
import de.hanno.hpengine.graphics.texture.TextureDescription
import de.hanno.hpengine.graphics.texture.TextureDimension
import de.hanno.hpengine.graphics.texture.TextureManagerBaseSystem
import de.hanno.hpengine.math.OmniCamera
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.transform.EntityMovementSystem
import org.joml.Vector3f
import org.joml.Vector4f
import org.koin.core.annotation.Single

class EnvironmentProbeComponent: Component() {
    var size = Vector3f(10f)
    var captureAnimatedObjects = false // TODO: Actually use
    val position = Vector3f() // TODO: Remove, make better renderstate for that
    var weight = 1f
    var movedInCycle = -1L
}

@Single
class EnvironmentProbesStateHolder(
    renderStateContext: RenderStateContext,
) {
    val state = renderStateContext.renderState.registerState {
        EnvironmentProbesState()
    }
    val probes = renderStateContext.renderState.registerState {
        mutableListOf<EnvironmentProbeComponent>()
    }
    internal val probeForBatches = renderStateContext.renderState.registerState { mutableMapOf<Int, Int>() }
    fun getProbeForBatch(renderState: RenderState, entityBufferIndex: Int): Int? = renderState[probeForBatches][entityBufferIndex]
}

private const val envProbeResolution = 512
private const val maxEnvProbes = 4

@All(EnvironmentProbeComponent::class, TransformComponent::class)
@Single(binds = [BaseSystem::class, EnvironmentProbeSystem::class, RenderSystem::class])
class EnvironmentProbeSystem(
    private val graphicsApi: GraphicsApi,
    config: Config,
    programManager: ProgramManager,
    private val renderStateContext: RenderStateContext,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val entityBuffer: EntityBuffer,
    private val defaultBatchesSystem: DefaultBatchesSystem,
    private val materialSystem: MaterialSystem,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val textureManager: TextureManagerBaseSystem,
    private val entityMovementSystem: EntityMovementSystem,
    private val environmentProbesStateHolder: EnvironmentProbesStateHolder,
) : BaseEntitySystem(), RenderSystem, Extractor {

    lateinit var environmentProbeComponentMapper: ComponentMapper<EnvironmentProbeComponent>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>

    val simpleColorProgramStatic = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(Define("COLOR_OUTPUT_0", true)),
        StaticDefaultUniforms(graphicsApi)
    )

    val cubeMapArray = graphicsApi.CubeMapArray(
        TextureDescription.CubeMapArrayDescription(
            TextureDimension(envProbeResolution, envProbeResolution, maxEnvProbes),
            InternalTextureFormat.RGBA8,
            TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR),
            WrapMode.Repeat,
        )
    )
    val depthCubeMapArray = graphicsApi.CubeMapArray(
        TextureDescription.CubeMapArrayDescription(
            TextureDimension(
                cubeMapArray.dimension.width,
                cubeMapArray.dimension.height,
                cubeMapArray.dimension.depth
            ),
            internalFormat = InternalTextureFormat.DEPTH_COMPONENT24,
            textureFilterConfig = TextureFilterConfig(minFilter = MinFilter.NEAREST),
            wrapMode = WrapMode.ClampToEdge,
        )
    )

    var cubeMapArrayRenderTarget = graphicsApi.RenderTarget(
        graphicsApi.FrameBuffer(graphicsApi.createDepthBuffer(depthCubeMapArray)),
        cubeMapArray.dimension.width,
        cubeMapArray.dimension.height,
        listOf(cubeMapArray),
        "EnvProbeCubeMapArrayRenderTarget",
        Vector4f(1f, 0f, 0f, 0f)
    ).apply {
        cubeMapViews.forEachIndexed { index, it ->
            textureManager.registerGeneratedCubeMap("EnvProbe[$index]", it)
        }
    }

    val renderTarget2D = graphicsApi.RenderTarget(
        graphicsApi.FrameBuffer(
            graphicsApi.DepthBuffer(
                cubeMapArray.dimension.width,
                cubeMapArray.dimension.height,
            )
        ),
        500,
        500,
        listOf(cubeMapArrayRenderTarget.cubeMapFaceViews.first()),
        "EnvProbeRenderTarget2D",
        Vector4f(1f, 0f, 0f, 0f)
    )
    private val omniCamera = OmniCamera(Vector3f())

    private val staticDirectPipeline: StateRef<DirectPipeline> = renderStateContext.renderState.registerState {
        object : DirectPipeline(
            graphicsApi,
            config,
            simpleColorProgramStatic,
            entitiesStateHolder,
            entityBuffer,
            primaryCameraStateHolder,
            defaultBatchesSystem,
            materialSystem
        ) {
            override fun RenderState.extractRenderBatches(camera: Camera) =
                this[defaultBatchesSystem.renderBatchesStatic]
        }
    }

    private val probeRenderedInCycle = mutableMapOf<Int, Long>()

    override fun render(renderState: RenderState): Unit = graphicsApi.run {
        val probeState = renderState[environmentProbesStateHolder.probes]
        if (probeState.isEmpty()) return

        depthMask = true
        depthTest = true
        cullFace = true

        renderTarget2D.use(true)

        graphicsApi.viewPort(0, 0, envProbeResolution, envProbeResolution)

        probeState.apply {
            val entitiesState = renderState[entitiesStateHolder.entitiesState]
            var index = 0
            forEach { probe ->
                val renderedInCycle = probeRenderedInCycle[index]

                // TODO: This doesn't work sometimes, fix me
                val needsRerender = renderedInCycle?.let { renderedInCycle ->
                    val probeHasMoved = probe.movedInCycle >= renderedInCycle

                    renderedInCycle <= entitiesState.staticEntityMovedInCycle
                            || renderedInCycle <= entitiesState.entityAddedInCycle
                            || !probeRenderedInCycle.containsKey(index)
                            || probeHasMoved
                } ?: true
                if (needsRerender) {
                    omniCamera.updatePosition(probe.position)
                    val diffueMipLevel0Loaded = (0..5).map { faceIndex ->
                        graphicsApi.framebufferDepthTexture(
                            cubeMapArrayRenderTarget.cubeMapDepthFaceViews[6 * index + faceIndex],
                            0
                        )
                        graphicsApi.clearDepthBuffer()
                        graphicsApi.framebufferTextureLayer(
                            0,
                            cubeMapArrayRenderTarget.cubeMapFaceViews[6 * index + faceIndex],
                            0,
                            0
                        )
                        graphicsApi.clearColorBuffer()

                        renderState[staticDirectPipeline].prepare(renderState, omniCamera.cameras[faceIndex])
                        renderState[staticDirectPipeline].draw(renderState, omniCamera.cameras[faceIndex])
                        val anyDiffuseTextureMip0Loaded =
                            renderState[staticDirectPipeline].preparedBatches.any { batch ->
                                materialSystem.run { batch.material.finishedLoadingInCycle > (renderedInCycle ?: 0) }
                            }
                        anyDiffuseTextureMip0Loaded
                    }
                    graphicsApi.generateMipMaps(cubeMapArrayRenderTarget.cubeMapViews[index])
                    if (diffueMipLevel0Loaded.all { it }) {
                        probeRenderedInCycle[index] = renderState.cycle
                        println("#######################")
                        println("$index rendered in cycle ${renderState.cycle}")
                    }
                }
                index++
            }
        }
    }

    override fun extract(renderState: RenderState) {
        renderState[environmentProbesStateHolder.probes].apply {
            clear()
            forEachEntity { entityId ->
                add(EnvironmentProbeComponent().apply {
                    this.position.set(transformComponentMapper[entityId].transform.position)
                    this.size.set(environmentProbeComponentMapper[entityId].size)
                    this.movedInCycle = environmentProbeComponentMapper[entityId].movedInCycle
                })
            }
        }
        val batches = renderState[environmentProbesStateHolder.probeForBatches]
        batches.clear()
        renderState[defaultBatchesSystem.renderBatchesStatic].forEach { batch ->
            renderState[environmentProbesStateHolder.probes].forEachIndexed { probeIndex, probe ->
                val min = Vector3f(probe.size).mul(-0.5f).add(probe.position)
                val max = Vector3f(probe.size).mul(0.5f).add(probe.position)
                val isInside = batch.meshMaxWorld.allLessThanOrEqual(max)
                        && batch.meshMinWorld.allGreaterThanOrEqual(min)

                if (isInside) {
                    batches[batch.entityBufferIndex] = probeIndex
                }
            }
        }
    }

    private fun Vector3f.allLessThanOrEqual(other: Vector3f) = x <= other.x && y <= other.y && z <= other.z
    private fun Vector3f.allGreaterThanOrEqual(other: Vector3f) = x >= other.x && y >= other.y && z >= other.z

    override fun processSystem() {
        var index = 0
        forEachEntity { entityId ->
            environmentProbeComponentMapper[entityId].movedInCycle =
                entityMovementSystem.cycleEntityHasMovedIn(entityId)
            index++
        }
    }
}

// TODO: Reimplement
//class EnvironmentProbeSystem(
//    val programManager: ProgramManager,
//    val config: Config,
//    val textureManager: TextureManager,
//    renderStateManager: RenderStateManager,
//    val gpuContext: GpuContext
//) : SimpleComponentSystem<EnvironmentProbe>(EnvironmentProbe::class.java), RenderSystem {
//    private val renderProbeCommandQueue = RenderProbeCommandQueue()
//    val environmentMapsArray: CubeMapArray
//    private val environmentMapsArray1: CubeMapArray
//    private val environmentMapsArray2: CubeMapArray
//    private val environmentMapsArray3: CubeMapArray
//    val cubeMapArrayRenderTarget: CubeMapArrayRenderTarget
//    val minPositions = BufferUtils.createFloatBuffer(100 * 3)
//    val maxPositions = BufferUtils.createFloatBuffer(100 * 3)
//    val weights = BufferUtils.createFloatBuffer(100 * 3)
//
//    override fun onComponentAdded(component: de.hanno.hpengine.engine.component.Component) {
//        super.onComponentAdded(component)
//
//        (component as? EnvironmentProbe)?.let { probe ->
//            val sampler = EnvironmentSampler(
//                probe.entity, probe,
//                256, 256, components.indexOf(probe),
//                this, programManager,
//                config, textureManager
//            )
//            probe.sampler = sampler
//        }
//
//        updateBuffers()
//    }
//    private inline val probes: List<EnvironmentProbe>
//        get() = components
//
//    val EnvironmentProbe.index: Int
//        get() = components.indexOf(this)
//
//    fun EnvironmentProbe.getTextureUnitIndex(gpuContext: GpuContext): Int {
//        val index = index
//        return gpuContext.maxTextureUnits - index - 1
//    }
//
//    val EnvironmentProbe.debugColor: Vector3f
//        get() {
//            val colorHelper = index.toFloat() / probes.size.toFloat()
//            val randomGenerator = Random()
//            randomGenerator.setSeed(colorHelper.toLong())
//            val random = randomGenerator.nextFloat()
//            return Vector3f(1 - colorHelper * colorHelper, (1 - colorHelper) * (1 - colorHelper), colorHelper * colorHelper)
//        }
//
//    private val transformBuffer = BufferUtils.createFloatBuffer(16)
//    private fun EnvironmentSampler.bindProgramSpecificsPerCubeMap(probe: EnvironmentProbe, program: Program<Uniforms>, renderState: RenderState) {
//        program.use()
//        program.setUniform("firstBounceForProbe", RENDER_PROBES_WITH_FIRST_BOUNCE)
//        program.setUniform("probePosition", probe.entity.transform.center)
//        program.setUniform("probeSize", probe.size)
//        program.setUniform("activePointLightCount", renderState.lightState.pointLights.size)
//        program.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
//        program.bindShaderStorageBuffer(5, renderState.lightState.pointLightBuffer)
//        program.setUniform("activeAreaLightCount", renderState.lightState.areaLights.size)
////        program.bindShaderStorageBuffer(6, renderState.lightState.areaLights.lightBuffer) // TODO: use arealight light buffer
//        for (i in 0 until Math.min(renderState.lightState.areaLights.size, AreaLightSystem.MAX_AREALIGHT_SHADOWMAPS)) {
//            val areaLight = renderState.lightState.areaLights[i]
//            gpuContext.bindTexture(9 + i, GlTextureTarget.TEXTURE_2D, renderState.lightState.areaLightDepthMaps[i])
//            program.setUniformAsMatrix4("areaLightShadowMatrices[$i]", areaLight.entity.transform.get(transformBuffer))
//        }
//        program.setUniform("probeIndex", probe.index)
//        bindEnvironmentProbePositions(cubeMapProgram)
//    }
//
//    private fun EnvironmentSampler.drawCubeMapSides(probe: EnvironmentProbe, urgent: Boolean, renderState: RenderState) {
//        val initialOrientation = entity.transform.rotation
//        val initialPosition = entity.transform.position
//        val light = renderState.directionalLightState[0]
//        gpuContext.bindTexture(8, environmentMapsArray)
//        gpuContext.bindTexture(10, getEnvironmentMapsArray(0))
//        gpuContext.disable(GlCap.DEPTH_TEST)
//        gpuContext.depthFunc = GlDepthFunc.LEQUAL
//        renderTarget.use(gpuContext, false)
//        val cubeMapProgram = cubeMapProgram
//        bindProgramSpecificsPerCubeMap(probe, cubeMapProgram, renderState)
//        var filteringRequired = false
//        val viewProjectionMatrices = Util.getCubeViewProjectionMatricesForPosition(entity.transform.position)
//        val viewMatrixBuffer = BufferUtils.createFloatBuffer(16)
//        val projectionMatrixBuffer = BufferUtils.createFloatBuffer(16)
//        val viewProjectionMatrixBuffer = BufferUtils.createFloatBuffer(16)
//        for (i in 0..5) {
//            viewProjectionMatrices.left[i][viewMatrixBuffer]
//            viewProjectionMatrices.right[i][projectionMatrixBuffer]
//            Matrix4f().set(viewProjectionMatrices.right[i]).mul(viewProjectionMatrices.left[i])[viewProjectionMatrixBuffer]
//            rotateForIndex(i, entity)
//            val fullReRenderRequired = urgent || !drawnOnce
//            val aPointLightHasMoved = false // TODO: use renderState.pointLightMovedInCycle
//            val areaLightHasMoved = false // TODO: implement properly !scene.getAreaLightSystem().getAreaLights().any { it.entity.hasMoved }
//            val reRenderLightingRequired = false // TODO: implement properly light!!.entity.hasMoved || aPointLightHasMoved || areaLightHasMoved
//            val noNeedToRedraw = !urgent && !fullReRenderRequired && !reRenderLightingRequired
//            if (noNeedToRedraw) {  // early exit if only static objects visible and lights didn't change
////				continue;
//            } else if (reRenderLightingRequired) {
////				cubeMapLightingProgram.use();
//            } else if (fullReRenderRequired) {
////				cubeMapProgram.use();
//            }
//            filteringRequired = true
//            gpuContext.depthMask = true
//            gpuContext.enable(GlCap.DEPTH_TEST)
//            gpuContext.depthFunc = GlDepthFunc.LEQUAL
//            cubeMapArrayRenderTarget.setCubeMapFace(3, 0, probe.index, i)
//            gpuContext.clearDepthAndColorBuffer()
//            drawEntities(renderState, cubeMapProgram, viewMatrixBuffer, projectionMatrixBuffer, viewProjectionMatrixBuffer)
//        }
//        if (filteringRequired) {
//            generateCubeMapMipMaps()
//        }
//        entity.transform.translation(initialPosition)
//        entity.transform.rotation(initialOrientation)
//    }
//
//
//    fun EnvironmentSampler.drawCubeMap(probe: EnvironmentProbe, urgent: Boolean, extract: RenderState) {
//        drawCubeMapSides(probe, urgent, extract)
//    }
//
//    fun EnvironmentSampler.generateCubeMapMipMaps() {
//        if (config.quality.isUsePrecomputedRadiance) {
//            _generateCubeMapMipMaps()
//            if (config.quality.isCalculateActualRadiance) {
//                val (_, cubemapArrayColorTextureId, _, internalFormat) = getEnvironmentMapsArray(3)
//                val cubeMapView = GL11.glGenTextures()
//                GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubemapArrayColorTextureId,
//                    internalFormat, 0, CUBEMAP_MIPMAP_COUNT,
//                    6 * probe.index, 6)
//                val cubemapCopy = cubeMapView //TextureManager.copyCubeMap(cubeMapView, RESOLUTION, RESOLUTION, internalFormat);
//                //				renderer.getTextureManager().generateMipMapsCubeMap(cubemapCopy);
//                val USE_OMPUTE_SHADER_FOR_RADIANCE = true
//                if (USE_OMPUTE_SHADER_FOR_RADIANCE) {
//                    calculateRadianceCompute(internalFormat, cubemapArrayColorTextureId, cubeMapView, cubemapCopy)
//                } else {
//                    calculateRadianceFragment(internalFormat, cubemapArrayColorTextureId, cubeMapView, cubemapCopy)
//                }
//                GL11.glDeleteTextures(cubeMapView)
//                //				GL11.glDeleteTextures(cubemapCopy);
//            }
//        } else {
//            _generateCubeMapMipMaps()
//        }
//    }
//
//    fun EnvironmentProbe.draw(extract: RenderState) {
//        draw(false, extract)
//    }
//
//    fun EnvironmentProbe.draw(urgent: Boolean, extract: RenderState) {
//        sampler.drawCubeMap(this, urgent, extract)
//    }
//    fun EnvironmentSampler.calculateRadianceCompute(internalFormat: Int,
//                                                    cubemapArrayColorTextureId: Int, cubeMapView: Int, cubemapCopy: Int) {
//        cubemapRadianceProgram.use()
//        val width = EnvironmentProbeSystem.RESOLUTION / 2
//        val height = EnvironmentProbeSystem.RESOLUTION / 2
//        for (i in 0..5) {
//            val indexOfFace = 6 * probe.index + i
//            for (z in 0 until EnvironmentProbeSystem.CUBEMAP_MIPMAP_COUNT) {
//                GL42.glBindImageTexture(z, cubemapArrayColorTextureId, z + 1, false, indexOfFace, GL15.GL_WRITE_ONLY, internalFormat)
//            }
//            gpuContext.bindTexture(8, GlTextureTarget.TEXTURE_CUBE_MAP, cubemapCopy)
//            cubemapRadianceProgram.setUniform("currentCubemapSide", i)
//            cubemapRadianceProgram.setUniform("currentProbe", probe.index)
//            cubemapRadianceProgram.setUniform("screenWidth", width.toFloat())
//            cubemapRadianceProgram.setUniform("screenHeight", height.toFloat())
//            bindEnvironmentProbePositions(cubemapRadianceProgram)
//            cubemapRadianceProgram.dispatchCompute(EnvironmentProbeSystem.RESOLUTION / 2 / 32, EnvironmentProbeSystem.RESOLUTION / 2 / 32, 1)
//        }
//    }
//
//    fun EnvironmentSampler.calculateRadianceFragment(internalFormat: Int,
//                                                     cubemapArrayColorTextureId: Int,
//                                                     cubeMapView: Int, cubemapCopy: Int) {
//        cubemapRadianceFragmentProgram.use()
//        val cubeMapArrayRenderTarget = cubeMapArrayRenderTarget
//        renderTarget.use(gpuContext, false)
//        gpuContext.bindTexture(8, GlTextureTarget.TEXTURE_CUBE_MAP, cubeMapView)
//        //GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, renderer.getEnvironmentMap().getTextureId());
//        for (i in 0..5) {
//            var width = RESOLUTION
//            var height = RESOLUTION
//            val indexOfFace = 6 * probe.index + i
//            for (z in 0 until CUBEMAP_MIPMAP_COUNT) {
//                //cubeMapArrayRenderTarget.setCubeMapFace(0, probe.getIndex(), indexOfFace);
//                renderTarget.setCubeMapFace(0, cubeMapView, i, z)
//                width /= 2
//                height /= 2
//                gpuContext.clearColorBuffer()
//                gpuContext.clearColor(0f, 0f, 0f, 0f)
//                cubemapRadianceFragmentProgram.setUniform("currentCubemapSide", i)
//                cubemapRadianceFragmentProgram.setUniform("currentProbe", probe.index)
//                cubemapRadianceFragmentProgram.setUniform("screenWidth", width.toFloat())
//                cubemapRadianceFragmentProgram.setUniform("screenHeight", height.toFloat())
//                bindEnvironmentProbePositions(cubemapRadianceFragmentProgram)
//                gpuContext.viewPort(0, 0, width, height)
//                fullscreenBuffer.draw()
//            }
//        }
//    }
//
//    fun EnvironmentSampler._generateCubeMapMipMaps() {
//        val use2DMipMapping = false
//        val (_, id, _, internalFormat) = getEnvironmentMapsArray(3)
//        if (use2DMipMapping) {
//            for (i in 0..5) {
//                val cubeMapFaceView = GL11.glGenTextures()
//                GL43.glTextureView(cubeMapFaceView, GL11.GL_TEXTURE_2D, id, internalFormat, 0, EnvironmentProbeSystem.CUBEMAP_MIPMAP_COUNT, 6 * probe.index + i, 1)
//                textureManager.generateMipMaps(GlTextureTarget.TEXTURE_2D, cubeMapFaceView)
//                GL11.glDeleteTextures(cubeMapFaceView)
//            }
//        } else {
//            val cubeMapView = GL11.glGenTextures()
//            GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, id, internalFormat, 0, EnvironmentProbeSystem.CUBEMAP_MIPMAP_COUNT, 6 * probe.index, 6)
//            textureManager.generateMipMaps(GlTextureTarget.TEXTURE_CUBE_MAP, cubeMapView)
//            GL11.glDeleteTextures(cubeMapView)
//        }
//    }
//
//    fun updateBuffers() {
//        val srcMinPositions = FloatArray(100 * 3)
//        val srcMaxPositions = FloatArray(100 * 3)
//        val srcWeights = FloatArray(100)
//        for (i in probes.indices) {
//            val box = probes[i].box
//            box.move(Vector3f(box.min).add(box.halfExtents).negate())
//            box.move(probes[i].entity.transform.position)
//            val min = Vector3f(box.min)
//            val max = Vector3f(box.max)
//            val weight = probes[i].weight
//            srcMinPositions[3 * i] = min.x
//            srcMinPositions[3 * i + 1] = min.y
//            srcMinPositions[3 * i + 2] = min.z
//            srcMaxPositions[3 * i] = max.x
//            srcMaxPositions[3 * i + 1] = max.y
//            srcMaxPositions[3 * i + 2] = max.z
//            srcWeights[i] = weight
//        }
//        minPositions.put(srcMinPositions)
//        maxPositions.put(srcMaxPositions)
//        weights.put(srcWeights)
//        minPositions.rewind()
//        maxPositions.rewind()
//        weights.rewind()
//    }
//
//    fun draw(urgent: Boolean = false) {
//        if (!config.quality.isDrawProbes) {
//            return
//        }
//        prepareProbeRendering()
//        val dynamicProbes = probes.stream().filter { probe: EnvironmentProbe -> probe.probeUpdate === EnvironmentProbe.Update.DYNAMIC }.collect(
//            Collectors.toList())
//        for (i in 1..dynamicProbes.size) {
//            val environmentProbe = dynamicProbes[i - 1]
//            addRenderProbeCommand(environmentProbe, urgent)
//        }
//    }
//
//    fun drawAlternating(camera: Entity, renderState: RenderState) {
//        if (!config.quality.isDrawProbes) {
//            return
//        }
//        prepareProbeRendering()
//        val dynamicProbes = renderState[extractedProbes].stream().filter { probe: EnvironmentProbe -> probe.probeUpdate === EnvironmentProbe.Update.DYNAMIC }.sorted { o1: EnvironmentProbe, o2: EnvironmentProbe -> java.lang.Float.compare(Vector3f(o1.entity.transform.center).sub(camera.transform.position.negate()).lengthSquared(), Vector3f(o2.entity.transform.center).sub(camera.transform.position.negate()).lengthSquared()) }.collect(
//            Collectors.toList())
//        for (i in 1..dynamicProbes.size) {
//            val environmentProbe = dynamicProbes[i - 1]
//            environmentProbe.draw(renderState)
//        }
//    }
//
//    fun prepareProbeRendering() {
//        gpuContext.depthMask = true
//        gpuContext.enable(GlCap.DEPTH_TEST)
//        gpuContext.enable(GlCap.CULL_FACE)
//        cubeMapArrayRenderTarget.use(gpuContext, false)
//    }
//
//    fun getEnvironmentMapsArray(index: Int): CubeMapArray = when (index) {
//        0 -> environmentMapsArray
//        1 -> environmentMapsArray1
//        2 -> environmentMapsArray2
//        3 -> environmentMapsArray3
//        else -> throw IllegalArgumentException("No env maps array with $index")
//    }
//
//    fun addRenderProbeCommand(probe: EnvironmentProbe?, urgent: Boolean) {
//        renderProbeCommandQueue.addProbeRenderCommand(probe, urgent)
//    }
//
//    fun addRenderProbeCommand(probe: EnvironmentProbe?) {
//        addRenderProbeCommand(probe, false)
//    }
//
//    override suspend fun update(deltaSeconds: Float) {
//        probes.forEach { it.update(deltaSeconds) }
//
//        updateBuffers()
//    }
//
//    override fun render(result: DrawResult, renderState: RenderState) {
//        drawAlternating(renderState.camera.entity, renderState)
//    }
//
//    val extractedProbes = renderStateManager.renderState.registerState { mutableListOf<EnvironmentProbe>() }
//    override fun extract(scene: Scene, renderState: RenderState, world: World) {
//        renderState.environmentProbesState.environmapsArray0Id = getEnvironmentMapsArray(0).id
//        renderState.environmentProbesState.environmapsArray3Id = getEnvironmentMapsArray(3).id
//        renderState.environmentProbesState.activeProbeCount = probes.size
//        renderState.environmentProbesState.environmentMapMin = minPositions
//        renderState.environmentProbesState.environmentMapMax = maxPositions
//        renderState.environmentProbesState.environmentMapWeights = weights
//        renderState[extractedProbes].apply {
//            clear()
//            addAll(components)
//        }
//
//    }
//    fun bindEnvironmentProbePositions(program: AbstractProgram<*>, state: EnvironmentProbeState) {
//        bindEnvironmentProbePositions(program, state.activeProbeCount, state.environmentMapMin, state.environmentMapMax, state.environmentMapWeights)
//    }
//
//    fun bindEnvironmentProbePositions(program: AbstractProgram<*>, activeProbeCount: Int = probes.size, minPositions: FloatBuffer = this.minPositions, maxPositions: FloatBuffer = this.maxPositions, weights: FloatBuffer = this.weights) {
//        program.setUniform("activeProbeCount", activeProbeCount)
//        program.setUniformVector3ArrayAsFloatBuffer("environmentMapMin", minPositions)
//        program.setUniformVector3ArrayAsFloatBuffer("environmentMapMax", maxPositions)
//        program.setUniformFloatArrayAsFloatBuffer("environmentMapWeights", weights)
//    }
//
//    init {
//        val dimension = TextureDimension.invoke(RESOLUTION, RESOLUTION, MAX_PROBES)
//        val filterConfig = TextureFilterConfig(MinFilter.LINEAR, MagFilter.LINEAR)
//        val wrapMode = GL11.GL_REPEAT
//        val gpuContext = graphicsApi
//        environmentMapsArray = CubeMapArray(gpuContext, dimension, filterConfig, GL30.GL_RGBA32F, wrapMode)
//        environmentMapsArray1 = CubeMapArray(gpuContext, dimension, filterConfig, GL11.GL_RGBA8, wrapMode)
//        environmentMapsArray2 = CubeMapArray(gpuContext, dimension, filterConfig, GL11.GL_RGBA8, wrapMode)
//        environmentMapsArray3 = CubeMapArray(gpuContext, dimension, filterConfig, GL11.GL_RGBA8, wrapMode)
//        cubeMapArrayRenderTarget = CubeMapArrayRenderTarget.invoke(
//            gpuContext,
//            RESOLUTION,
//            RESOLUTION,
//            "CubeMapArrayRenderTarget",
//            Vector4f(0f, 0f, 0f, 0f),
//            environmentMapsArray,
//            environmentMapsArray1,
//            environmentMapsArray2,
//            environmentMapsArray3
//        )
//    }
//
//    companion object {
//        const val MAX_PROBES = 25
//        const val RESOLUTION = 256
//        val CUBEMAP_MIPMAP_COUNT = Util.calculateMipMapCount(RESOLUTION)
//        var DEFAULT_PROBE_UPDATE = EnvironmentProbe.Update.DYNAMIC
//
//    }
//}