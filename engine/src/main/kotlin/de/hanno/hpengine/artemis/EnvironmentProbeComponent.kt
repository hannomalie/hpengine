package de.hanno.hpengine.artemis

import com.artemis.Component
import de.hanno.hpengine.graphics.renderer.environmentsampler.EnvironmentSampler
import de.hanno.hpengine.transform.AABB
import de.hanno.hpengine.Transform
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.RenderStateContext
import de.hanno.hpengine.graphics.state.EnvironmentProbesState
import de.hanno.hpengine.transform.TransformSpatial
import org.joml.Vector3f
import org.joml.Vector3fc

class EnvironmentProbeComponent: Component() {
    var size = Vector3f(10f)
    var weight = 1f

    val spatial = TransformSpatial(Transform(), AABB(Vector3f(size).div(2f).negate(), Vector3f(size).div(2f)))
    val box = spatial.boundingVolume

    // TODO: I don't want to have this here
    lateinit var sampler: EnvironmentSampler

    fun move(amount: Vector3f) {
        box.move(amount)
    }

    fun contains(min: Vector3fc?, max: Vector3fc?) = box.contains(min!!) && box.contains(max!!)
    operator fun contains(minMaxWorld: AABB) = contains(minMaxWorld.min, minMaxWorld.max)
}

context(GpuContext, RenderStateContext)
class EnvironmentProbesStateHolder {
    val environmentProbesState = renderState.registerState {
        EnvironmentProbesState()
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
//        val gpuContext = gpuContext
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