package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.BindlessTextures
import de.hanno.hpengine.engine.graphics.DrawParameters
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.OpenGLContext
import de.hanno.hpengine.engine.graphics.light.area.AreaLight
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem.Companion.MAX_POINTLIGHT_SHADOWMAPS
import de.hanno.hpengine.engine.graphics.light.tube.TubeLight
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.CullMode.BACK
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.BLEND
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.CULL_FACE
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.DEPTH_TEST
import de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc.LESS
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_CUBE_MAP_ARRAY
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DrawLinesExtension
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.PixelPerfectPickingExtension
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.ForwardRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.GPUCulledMainPipeline
import de.hanno.hpengine.engine.graphics.renderer.pipelines.Pipeline
import de.hanno.hpengine.engine.graphics.renderer.pipelines.SimplePipeline
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.shader.getShaderSource
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.graphics.state.StateRef
import de.hanno.hpengine.engine.model.DataChannels
import de.hanno.hpengine.engine.model.QuadVertexBuffer
import de.hanno.hpengine.engine.model.VertexBuffer
import de.hanno.hpengine.engine.model.draw
import de.hanno.hpengine.engine.model.drawDebugLines
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.scene.EnvironmentProbeManager.bindEnvironmentProbePositions
import de.hanno.hpengine.log.ConsoleLogger.getLogger
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.glFinish
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42
import org.lwjgl.opengl.GL43
import java.io.File
import java.nio.FloatBuffer
import java.util.ArrayList
import java.util.EnumSet
import java.util.function.Consumer
import javax.vecmath.Vector2f

class DeferredRenderer
            @Throws(Exception::class) constructor(private val materialManager: MaterialManager,
            val engineContext: EngineContext<OpenGl>,
            val deferredRenderingBuffer: DeferredRenderingBuffer) : RenderSystem {

    private val backend: Backend<OpenGl> = engineContext.backend
    val gpuContext = engineContext.gpuContext.backend.gpuContext
    val programManager = backend.programManager
    val renderState = engineContext.renderStateManager.renderState
    val window = engineContext.window

    init {
//        throwIfGpuFeaturesMissing()
        gpuContext.enableSeamlessCubeMapFiltering()
    }

    private fun throwIfGpuFeaturesMissing() {
        val requiredFeatures = listOf(BindlessTextures, DrawParameters)
        when (val result = gpuContext.isSupported(requiredFeatures)) {
            is GpuContext.SupportResult.UnSupported -> {
                val msg = "Cannot create DeferredRenderer with given OpenGlContext\n" +
                        "Missing features: ${result.unsupportedFeatures.joinToString(", ")}"
                throw IllegalArgumentException(msg)
            }
        }
    }

    private val sixDebugBuffers: ArrayList<VertexBuffer> = gpuContext.setupBuffers()

    private val gBuffer: DeferredRenderingBuffer = deferredRenderingBuffer
    private val forwardRenderer = ForwardRenderExtension(engineContext)

    private val renderToQuadProgram = programManager.getProgram(getShaderSource(File(Shader.directory + "passthrough_vertex.glsl")), getShaderSource(File(Shader.directory + "simpletexture_fragment.glsl")))
    private val debugFrameProgram = programManager.getProgram(getShaderSource(File(Shader.directory + "passthrough_vertex.glsl")), getShaderSource(File(Shader.directory + "debugframe_fragment.glsl")))

    private val firstpassProgram = programManager.getProgram(getShaderSource(File(Shader.directory + "first_pass_vertex.glsl")), getShaderSource(File(Shader.directory + "first_pass_fragment.glsl")))
    private val firstpassProgramAnimated = programManager.getProgram(
        getShaderSource(File(Shader.directory + "first_pass_vertex.glsl")),
        getShaderSource(File(Shader.directory + "first_pass_fragment.glsl")), Defines(Define.getDefine("ANIMATED", true))
    )

    private val secondPassTubeProgram = programManager.getProgram(getShaderSource(File(Shader.directory + "second_pass_point_vertex.glsl")), getShaderSource(File(Shader.directory + "second_pass_tube_fragment.glsl")))
    private val secondPassAreaProgram = programManager.getProgram(getShaderSource(File(Shader.directory + "second_pass_area_vertex.glsl")), getShaderSource(File(Shader.directory + "second_pass_area_fragment.glsl")))
    private val instantRadiosityProgram = programManager.getProgram(getShaderSource(File(Shader.directory + "second_pass_area_vertex.glsl")), getShaderSource(File(Shader.directory + "second_pass_instant_radiosity_fragment.glsl")))
    private val postProcessProgram = programManager.getProgram(getShaderSource(File(Shader.directory + "passthrough_vertex.glsl")), getShaderSource(File(Shader.directory + "postprocess_fragment.glsl")))

    private val aoScatteringProgram = programManager.getProgramFromFileNames("passthrough_vertex.glsl", "scattering_ao_fragment.glsl")
    private val reflectionProgram = programManager.getProgramFromFileNames("passthrough_vertex.glsl", "reflections_fragment.glsl")
    private val skyBoxDepthProgram = programManager.getProgramFromFileNames("mvp_vertex.glsl", "skybox_depth.glsl")
//    private val probeFirstpassProgram = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "probe_first_pass_fragment.glsl")
    private val depthPrePassProgram = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "depth_prepass_fragment.glsl")
    private val tiledDirectLightingProgram = programManager.getComputeProgram("tiled_direct_lighting_compute.glsl")
    private val tiledProbeLightingProgram = programManager.getComputeProgram("tiled_probe_lighting_compute.glsl")

    val renderExtensions: MutableList<RenderExtension<OpenGl>> = mutableListOf()
    //	private final DirectionalLightShadowMapExtension directionalLightShadowMapExtension;
    private val mainPipelineRef: StateRef<Pipeline>

    init {
        if (engineContext.gpuContext !is OpenGLContext) {
            throw IllegalStateException("Cannot use this DeferredRenderer with a non-OpenGlContext!")
        }
        //		directionalLightShadowMapExtension = new DirectionalLightShadowMapExtension(engineContext);

        registerRenderExtension(DrawLinesExtension(engineContext, programManager))
        //		TODO: This seems to be broken with the new texture implementations
        //		registerRenderExtension(new VoxelConeTracingExtension(engineContext, directionalLightShadowMapExtension, this));
        //        registerRenderExtension(new EvaluateProbeRenderExtension(managerContext));
        registerRenderExtension(forwardRenderer)
        registerRenderExtension(PixelPerfectPickingExtension())

        mainPipelineRef = renderState.registerState<Pipeline> {
            if(gpuContext.isSupported(BindlessTextures)) {
                GPUCulledMainPipeline(engineContext, this)
            } else {
                SimplePipeline(engineContext)
            }
        }
    }

    private fun registerRenderExtension(extension: RenderExtension<OpenGl>) {
        renderExtensions.add(extension)
    }

    private fun GpuContext<OpenGl>.setupBuffers(): ArrayList<VertexBuffer> {
        return calculate {
            val sixDebugBuffers = object : ArrayList<VertexBuffer>() {
                init {
                    val height = -2f / 3f
                    val width = 2f
                    val widthDiv = width / 6f
                    for (i in 0..5) {
                        val quadVertexBuffer = QuadVertexBuffer(backend.gpuContext, Vector2f(-1f + i * widthDiv, -1f), Vector2f(-1 + (i + 1) * widthDiv, height))
                        add(quadVertexBuffer)
                        quadVertexBuffer.upload()
                    }
                }
            }

            gpuContext.getExceptionOnError("setupBuffers")
            sixDebugBuffers
        }
    }

    private fun GpuContext<OpenGl>.enableSeamlessCubeMapFiltering() {
        execute("GpuContext<OpenGl>.setUpGBuffer") {
            this@enableSeamlessCubeMapFiltering.enable(GlCap.TEXTURE_CUBE_MAP_SEAMLESS)
            this@enableSeamlessCubeMapFiltering.getExceptionOnError("setupGBuffer")
        }
    }

    override fun render(result: DrawResult, state: RenderState): Unit = profiled("Frame") {
        gpuContext.getExceptionOnError("Frame")?.let { throw it }

        val firstPassResult = result.firstPassResult
        profiled("First pass") {

            val pipeline = state.getState(mainPipelineRef)

            val camera = state.camera

            gpuContext.depthMask(true)
            deferredRenderingBuffer.use(gpuContext, true)

            profiled("Set GPU state") {
                gpuContext.enable(CULL_FACE)
                gpuContext.depthMask(true)
                gpuContext.enable(DEPTH_TEST)
                gpuContext.depthFunc(LESS)
                gpuContext.disable(GlCap.BLEND)
            }

            gpuContext.exceptionOnError("Before draw entities")

            profiled("Draw entities") {
                if (engineContext.config.debug.isDrawScene) {
                    pipeline.draw(state, firstpassProgram, firstpassProgramAnimated, firstPassResult)
                }
            }

            if (!engineContext.config.debug.isUseDirectTextureOutput) {
                backend.gpuContext.bindTexture(6, TEXTURE_2D, state.directionalLightState.shadowMapId)
                for (extension in renderExtensions) {
                    profiled("RenderExtension " + extension.javaClass.simpleName) {
                        extension.renderFirstPass(backend, gpuContext, firstPassResult, state)
                    }
                }
            }

            gpuContext.enable(CULL_FACE)

            profiled("Generate Mipmaps of colormap") {
                backend.textureManager.generateMipMaps(TEXTURE_2D, gBuffer.colorReflectivenessMap)
            }
        }

        if (!engineContext.config.debug.isUseDirectTextureOutput) {
            //			GPUProfiler.start("Shadowmap pass");
            //			directionalLightShadowMapExtension.renderFirstPass(backend, gpuContext, result.getFirstPassResult(), renderState);
            //            managerContext.getScene().getAreaLightSystem().renderAreaLightShadowMaps(renderState);
            //            managerContext.getScene().getPointLightSystem().getShadowMapStrategy().renderPointLightShadowMaps(renderState);
            //			GPUProfiler.end();

            profiled("Second pass") {
                val secondPassResult = result.secondPassResult
                val camera1 = state.camera
                val camPosition = Vector3f(camera1.getPosition())
                camPosition.add(camera1.getViewDirection().mul(-camera1.near))
                val camPositionV4 = Vector4f(camPosition.x, camPosition.y, camPosition.z, 0f)

                val viewMatrix = camera1.viewMatrixAsBuffer
                val projectionMatrix = camera1.projectionMatrixAsBuffer

                doTubeLights(state.lightState.tubeLights, camPositionV4, viewMatrix, projectionMatrix)

                doAreaLights(state.lightState.areaLights, viewMatrix, projectionMatrix, state)

                if (!engineContext.config.debug.isUseDirectTextureOutput) {
                    profiled("Extensions") {
                        gpuContext.bindTexture(6, TEXTURE_2D, state.directionalLightState.shadowMapId)
                        for (extension in renderExtensions) {
                            extension.renderSecondPassFullScreen(state, secondPassResult)
                        }
                    }
                }

                backend.gpuContext.disable(BLEND)
                gBuffer.lightAccumulationBuffer.unuse(gpuContext)

                renderAOAndScattering(state)

                profiled("MipMap generation AO and light buffer") {
                    backend.gpuContext.activeTexture(0)
                    backend.textureManager.generateMipMaps(TEXTURE_2D, gBuffer.lightAccumulationMapOneId)
                }

                if (engineContext.config.quality.isUseGi) {
                    GL11.glDepthMask(false)
                    backend.gpuContext.disable(DEPTH_TEST)
                    backend.gpuContext.disable(BLEND)
                    backend.gpuContext.cullFace(BACK)
                    renderReflections(viewMatrix, projectionMatrix, state)
                } else {
                    gBuffer.reflectionBuffer.use(gpuContext, true)
                    gBuffer.reflectionBuffer.unuse(gpuContext)
                }

                for (extension in renderExtensions) {
                    extension.renderSecondPassHalfScreen(state, secondPassResult)
                }

                profiled("Blurring") {
                    //        GPUProfiler.start("LightAccumulationMap");
                    //        TextureManager.getInstance().blurHorinzontal2DTextureRGBA16F(gBuffer.getLightAccumulationMapOneId(), engine.getConfig().WIDTH, engine.getConfig().HEIGHT, 7, 8);
                    //        GPUProfiler.end();
                    profiled("Scattering texture") {
                        if (engineContext.config.effects.isScattering || engineContext.config.quality.isUseAmbientOcclusion) {
                            backend.textureManager.blur2DTextureRGBA16F(gBuffer.halfScreenBuffer.renderedTexture, engineContext.config.width / 2, engineContext.config.height / 2, 0, 0)
                        }
                    }
                    //        TextureManager.getInstance().blur2DTextureRGBA16F(gBuffer.getHalfScreenBuffer().getRenderedTexture(), engine.getConfig().WIDTH / 2, engine.getConfig().HEIGHT / 2, 0, 0);
                    //        Renderer.getInstance().blur2DTexture(gBuffer.getHalfScreenBuffer().getRenderedTexture(), 0, engine.getConfig().WIDTH / 2, engine.getConfig().HEIGHT / 2, GL30.GL_RGBA16F, false, 1);
                    //		renderer.blur2DTexture(gBuffer.getLightAccumulationMapOneId(), 0, engine.getConfig().WIDTH, engine.getConfig().HEIGHT, GL30.GL_RGBA16F, false, 1);
                    //		renderer.blur2DTexture(getLightAccumulationMapOneId(), 0, (int)(engine.getConfig().WIDTH*SECONDPASSSCALE), (int)(engine.getConfig().HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);
                    //		Renderer.getInstance().blur2DTexture(gBuffer.getAmbientOcclusionMapId(), 0, (int)(engine.getConfig().WIDTH), (int)(engine.getConfig().HEIGHT), GL30.GL_RGBA16F, false, 1);
                    //		renderer.blur2DTextureBilateral(getLightAccumulationMapOneId(), 0, (int)(renderer.WIDTH*SECONDPASSSCALE), (int)(renderer.HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);

                    //		renderer.blur2DTexture(halfScreenTarget.getRenderedTexture(0), (int)(renderer.WIDTH*0.5), (int)(renderer.HEIGHT*0.5), GL11.GL_RGBA8, false, 1);
                    //		renderer.blur2DTexture(halfScreenTarget.getRenderedTexture(1), (int)(renderer.WIDTH*0.5), (int)(renderer.HEIGHT*0.5), GL11.GL_RGBA8, false, 1);

                    backend.gpuContext.cullFace(BACK)
                    backend.gpuContext.depthFunc(LESS)
                }

            }


            window.frontBuffer.use(gpuContext, true)
            profiled("Post processing") {
                postProcessProgram.use()
                backend.gpuContext.bindTexture(0, TEXTURE_2D, deferredRenderingBuffer.finalBuffer.getRenderedTexture(0))
                postProcessProgram.setUniform("screenWidth", engineContext.config.width.toFloat())
                postProcessProgram.setUniform("screenHeight", engineContext.config.height.toFloat())
                postProcessProgram.setUniform("worldExposure", state.camera.exposure)
                postProcessProgram.setUniform("AUTO_EXPOSURE_ENABLED", engineContext.config.effects.isAutoExposureEnabled)
                postProcessProgram.setUniform("usePostProcessing", engineContext.config.effects.isEnablePostprocessing)
                postProcessProgram.setUniform("cameraRightDirection", state.camera.getRightDirection())
                postProcessProgram.setUniform("cameraViewDirection", state.camera.getViewDirection())

                postProcessProgram.setUniform("seconds", state.deltaInS)
                postProcessProgram.bindShaderStorageBuffer(0, deferredRenderingBuffer.exposureBuffer)
                //        postProcessProgram.bindShaderStorageBuffer(1, managerContext.getRenderer().getMaterialManager().getMaterialBuffer());
                backend.gpuContext.bindTexture(1, TEXTURE_2D, deferredRenderingBuffer.normalMap)
                backend.gpuContext.bindTexture(2, TEXTURE_2D, deferredRenderingBuffer.motionMap)
                backend.gpuContext.bindTexture(3, TEXTURE_2D, deferredRenderingBuffer.lightAccumulationMapOneId)
                backend.gpuContext.bindTexture(4, TEXTURE_2D, backend.textureManager.lensFlareTexture.id)
                gpuContext.fullscreenBuffer.draw()
            }
        } else {
            backend.gpuContext.disable(DEPTH_TEST)
            window.frontBuffer.use(gpuContext, true)
            drawToQuad(engineContext.config.debug.directTextureOutputTextureIndex)
        }
        if (engineContext.config.debug.isDebugframeEnabled) {
            //			ArrayList<Texture> textures = new ArrayList<>(backend.getTextureManager().getTextures().values());
            drawToQuad(gBuffer.forwardBuffer.renderedTexture, backend.gpuContext.debugBuffer, debugFrameProgram)
            //			drawToQuad(managerContext.getScene().getAreaLightSystem().getDepthMapForAreaLight(managerContext.getScene().getAreaLightSystem().getAreaLights().get(0)), managerContext.getGpuContext().getDebugBuffer(), managerContext.getProgramManager().getDebugFrameProgram());

            //			for(int i = 0; i < 6; i++) {
            //				drawToQuad(managerContext.getEnvironmentProbeManager().getProbes().get(0).getSampler().getCubeMapFaceViews()[3][i], sixDebugBuffers.get(i));
            //			}


            //			DEBUG POINT LIGHT SHADOWS

            //			int faceView = OpenGLContext.getInstance().genTextures();
            //			GL43.glTextureView(faceView, GlTextureTarget.TEXTURE_2D.glTarget, directionalLightSystem.getPointLightDepthMapsArrayBack(),
            //					GL30.GL_RGBA16F, 0, 1, 0, 1);
            //			drawToQuad(faceView, sixDebugBuffers.get(0));
            //			faceView = OpenGLContext.getInstance().genTextures();
            //			GL43.glTextureView(faceView, GlTextureTarget.TEXTURE_2D.glTarget, directionalLightSystem.getPointLightDepthMapsArrayFront(),
            //					GL30.GL_RGBA16F, 0, 1, 0, 1);
            //			drawToQuad(faceView, sixDebugBuffers.get(1));
            //			GL11.glDeleteTextures(faceView);


            //			DEBUG PROBES

            //            int[] faceViews = new int[6];
            //			int index = 0;
            //            for(int i = 0; i < 6; i++) {
            //                faceViews[i] = managerContext.getGpuContext().genTextures();
            //				int cubeMapArray = managerContext.getScene().getProbeSystem().getStrategy().getCubemapArrayRenderTarget().getCubeMapArray().getTextureID();
            //				GL43.glTextureView(faceViews[i], GlTextureTarget.TEXTURE_2D.glTarget, cubeMapArray, GL_RGBA16F, 0, 10, (6*index)+i, 1);
            //				drawToQuad(faceViews[i], sixDebugBuffers.get(i), managerContext.getProgramManager().getDebugFrameProgram());
            //			}
            //            for(int i = 0; i < 6; i++) {
            //                GL11.glDeleteTextures(faceViews[i]);
            //            }
        }
    }

    fun drawToQuad(texture: Int) {
        drawToQuad(texture, backend.gpuContext.fullscreenBuffer, renderToQuadProgram)
    }

    fun drawToQuad(texture: Int, buffer: VertexBuffer) {
        drawToQuad(texture, buffer, renderToQuadProgram)
    }

    private fun drawToQuad(texture: Int, buffer: VertexBuffer, program: Program) {
        program.use()
        backend.gpuContext.disable(GlCap.DEPTH_TEST)

        backend.gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, texture)
        backend.gpuContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, gBuffer.normalMap)

        buffer.draw()
    }


    private fun doTubeLights(tubeLights: List<TubeLight>,
                             camPositionV4: Vector4f, viewMatrix: FloatBuffer,
                             projectionMatrix: FloatBuffer) {


        if (tubeLights.isEmpty()) {
            return
        }

        secondPassTubeProgram.use()
        secondPassTubeProgram.setUniform("screenWidth", engineContext.config.width.toFloat())
        secondPassTubeProgram.setUniform("screenHeight", engineContext.config.height.toFloat())
        secondPassTubeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
        secondPassTubeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
        for (tubeLight in tubeLights) {
            val camInsideLightVolume = tubeLight.minMaxWorld.contains(camPositionV4)
            if (camInsideLightVolume) {
                GL11.glCullFace(GL11.GL_FRONT)
                GL11.glDepthFunc(GL11.GL_GEQUAL)
            } else {
                GL11.glCullFace(GL11.GL_BACK)
                GL11.glDepthFunc(GL11.GL_LEQUAL)
            }
            secondPassTubeProgram.setUniform("lightPosition", tubeLight.entity.position)
            secondPassTubeProgram.setUniform("lightStart", tubeLight.start)
            secondPassTubeProgram.setUniform("lightEnd", tubeLight.end)
            secondPassTubeProgram.setUniform("lightOuterLeft", tubeLight.outerLeft)
            secondPassTubeProgram.setUniform("lightOuterRight", tubeLight.outerRight)
            secondPassTubeProgram.setUniform("lightRadius", tubeLight.radius)
            secondPassTubeProgram.setUniform("lightLength", tubeLight.length)
            secondPassTubeProgram.setUniform("lightDiffuse", tubeLight.color)
            tubeLight.draw(secondPassTubeProgram)
        }
    }

    private fun doAreaLights(areaLights: List<AreaLight>, viewMatrix: FloatBuffer, projectionMatrix: FloatBuffer, renderState: RenderState) {

        backend.gpuContext.disable(CULL_FACE)
        backend.gpuContext.disable(DEPTH_TEST)
        if (areaLights.isEmpty()) {
            return
        }

        profiled("Area light: " + areaLights.size) {

            secondPassAreaProgram.use()
            secondPassAreaProgram.setUniform("screenWidth", engineContext.config.width.toFloat())
            secondPassAreaProgram.setUniform("screenHeight", engineContext.config.height.toFloat())
            secondPassAreaProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
            secondPassAreaProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
            for (areaLight in areaLights) {
                //            boolean camInsideLightVolume = new AABB(areaLight.getPosition(), 2*areaLight.getScale().x, 2*areaLight.getScale().y, 2*areaLight.getScale().z).contains(camPositionV4);
                //            if (camInsideLightVolume) {
                //                GL11.glCullFace(GL11.GL_FRONT);
                //                GL11.glDepthFunc(GL11.GL_GEQUAL);
                //            } else {
                //                GL11.glCullFace(GL11.GL_BACK);
                //                GL11.glDepthFunc(GL11.GL_LEQUAL);
                //            }
                secondPassAreaProgram.setUniform("lightPosition", areaLight.entity.position)
                secondPassAreaProgram.setUniform("lightRightDirection", areaLight.entity.rightDirection)
                secondPassAreaProgram.setUniform("lightViewDirection", areaLight.entity.viewDirection)
                secondPassAreaProgram.setUniform("lightUpDirection", areaLight.entity.upDirection)
                secondPassAreaProgram.setUniform("lightWidth", areaLight.width)
                secondPassAreaProgram.setUniform("lightHeight", areaLight.height)
                secondPassAreaProgram.setUniform("lightRange", areaLight.range)
                secondPassAreaProgram.setUniform("lightDiffuse", areaLight.color)
                secondPassAreaProgram.setUniformAsMatrix4("shadowMatrix", areaLight.viewProjectionMatrixAsBuffer)

                // TODO: Add textures to arealights
                //            try {
                //                GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
                //                Texture lightTexture = renderer.getTextureManager().getDiffuseTexture("brick.hptexture");
                //                GL11.glBindTexture(GL11.GL_TEXTURE_2D, lightTexture.getTextureId());
                //            } catch (IOException e) {
                //                e.printStackTrace();
                //            }
                gpuContext.bindTexture(9, GlTextureTarget.TEXTURE_2D, AreaLightSystem.getDepthMapForAreaLight(renderState.lightState.areaLights, renderState.lightState.areaLightDepthMaps, areaLight))
                gpuContext.fullscreenBuffer.draw()
                //            areaLight.getVertexBuffer().drawDebug();
            }
        }

    }

    private fun renderAOAndScattering(renderState: RenderState) = profiled("Scattering and AO") {
        if (!engineContext.config.quality.isUseAmbientOcclusion && !engineContext.config.effects.isScattering) {
            return
        }
        gBuffer.halfScreenBuffer.use(gpuContext, true)
        backend.gpuContext.disable(DEPTH_TEST)
        backend.gpuContext.bindTexture(0, TEXTURE_2D, gBuffer.positionMap)
        backend.gpuContext.bindTexture(1, TEXTURE_2D, gBuffer.normalMap)
        backend.gpuContext.bindTexture(2, TEXTURE_2D, gBuffer.colorReflectivenessMap)
        backend.gpuContext.bindTexture(3, TEXTURE_2D, gBuffer.motionMap)
        backend.gpuContext.bindTexture(6, TEXTURE_2D, renderState.directionalLightState.shadowMapId)
        renderState.lightState.pointLightShadowMapStrategy.bindTextures()
        if(renderState.environmentProbesState.environmapsArray3Id > 0) {
            backend.gpuContext.bindTexture(8, TEXTURE_CUBE_MAP_ARRAY, renderState.environmentProbesState.environmapsArray3Id)
        }

        //		if(directionalLightShadowMapExtension.getVoxelConeTracingExtension() != null) {
        //			gpuContext.bindTexture(13, TEXTURE_3D, directionalLightShadowMapExtension.getVoxelConeTracingExtension().getVoxelGrids().get(0).getCurrentVoxelSource());
        //		}

        //		halfScreenBuffer.setTargetTexture(halfScreenBuffer.getRenderedTexture(), 0);
        aoScatteringProgram.use()
        aoScatteringProgram.setUniform("eyePosition", renderState.camera.getPosition())
        aoScatteringProgram.setUniform("useAmbientOcclusion", engineContext.config.quality.isUseAmbientOcclusion)
        aoScatteringProgram.setUniform("ambientOcclusionRadius", engineContext.config.effects.ambientocclusionRadius)
        aoScatteringProgram.setUniform("ambientOcclusionTotalStrength", engineContext.config.effects.ambientocclusionTotalStrength)
        aoScatteringProgram.setUniform("screenWidth", engineContext.config.width.toFloat() / 2)
        aoScatteringProgram.setUniform("screenHeight", engineContext.config.height.toFloat() / 2)
        aoScatteringProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
        aoScatteringProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
        aoScatteringProgram.setUniform("timeGpu", System.currentTimeMillis().toInt())
        //		aoScatteringProgram.setUniform("useVoxelGrid", directionalLightShadowMapExtension.getVoxelConeTracingExtension() != null);
        //		if(directionalLightShadowMapExtension.getVoxelConeTracingExtension() != null) {
        //			aoScatteringProgram.bindShaderStorageBuffer(5, renderState.getState(directionalLightShadowMapExtension.getVoxelConeTracingExtension().getVoxelGridBufferRef()).getVoxelGridBuffer());
        //		}

        aoScatteringProgram.setUniform("maxPointLightShadowmaps", MAX_POINTLIGHT_SHADOWMAPS)
        aoScatteringProgram.setUniform("pointLightCount", renderState.lightState.pointLights.size)
        aoScatteringProgram.bindShaderStorageBuffer(2, renderState.lightState.pointLightBuffer)
        aoScatteringProgram.bindShaderStorageBuffer(3, renderState.directionalLightBuffer)

        bindEnvironmentProbePositions(aoScatteringProgram, renderState.environmentProbesState)
        gpuContext.fullscreenBuffer.draw()
        profiled("generate mipmaps") {
            backend.gpuContext.enable(DEPTH_TEST)
            backend.textureManager.generateMipMaps(TEXTURE_2D, gBuffer.halfScreenBuffer.renderedTexture)
        }
    }

    private fun renderReflections(viewMatrix: FloatBuffer, projectionMatrix: FloatBuffer, renderState: RenderState) = profiled("Reflections and AO") {
        val gBuffer = deferredRenderingBuffer
        val reflectionBuffer = gBuffer.reflectionBuffer

        backend.gpuContext.bindTexture(0, TEXTURE_2D, gBuffer.positionMap)
        backend.gpuContext.bindTexture(1, TEXTURE_2D, gBuffer.normalMap)
        backend.gpuContext.bindTexture(2, TEXTURE_2D, gBuffer.colorReflectivenessMap)
        backend.gpuContext.bindTexture(3, TEXTURE_2D, gBuffer.motionMap)
        backend.gpuContext.bindTexture(4, TEXTURE_2D, gBuffer.lightAccumulationMapOneId)
        backend.gpuContext.bindTexture(5, TEXTURE_2D, gBuffer.finalMap)
        backend.gpuContext.bindTexture(6, backend.textureManager.cubeMap!!)
        //        GL13.glActiveTexture(GL13.GL_TEXTURE0 + 7);
        //        reflectionBuffer.getRenderedTexture(0);
        backend.gpuContext.bindTexture(8, TEXTURE_CUBE_MAP_ARRAY, renderState.environmentProbesState.environmapsArray3Id)
        backend.gpuContext.bindTexture(9, backend.textureManager.cubeMap!!)
        backend.gpuContext.bindTexture(10, TEXTURE_CUBE_MAP_ARRAY, renderState.environmentProbesState.environmapsArray0Id)
        backend.gpuContext.bindTexture(11, TEXTURE_2D, reflectionBuffer.renderedTexture)

        val copyTextureId = GL11.glGenTextures()
        backend.gpuContext.bindTexture(11, TEXTURE_2D, copyTextureId)
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, reflectionBuffer.width, reflectionBuffer.height, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, null as FloatBuffer?)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL43.glCopyImageSubData(reflectionBuffer.renderedTexture, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                copyTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                reflectionBuffer.width, reflectionBuffer.height, 1)
        backend.gpuContext.bindTexture(11, TEXTURE_2D, copyTextureId)

        if (!USE_COMPUTESHADER_FOR_REFLECTIONS) {
            reflectionBuffer.use(gpuContext, true)
            reflectionProgram.use()
            reflectionProgram.setUniform("N", IMPORTANCE_SAMPLE_COUNT)
            reflectionProgram.setUniform("useAmbientOcclusion", engineContext.config.quality.isUseAmbientOcclusion)
            reflectionProgram.setUniform("useSSR", engineContext.config.quality.isUseSSR)
            reflectionProgram.setUniform("screenWidth", engineContext.config.width.toFloat())
            reflectionProgram.setUniform("screenHeight", engineContext.config.height.toFloat())
            reflectionProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
            reflectionProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
            bindEnvironmentProbePositions(reflectionProgram, renderState.environmentProbesState)
            reflectionProgram.setUniform("activeProbeCount", renderState.environmentProbesState.activeProbeCount)
            reflectionProgram.bindShaderStorageBuffer(0, gBuffer.exposureBuffer)
            gpuContext.fullscreenBuffer.draw()
            reflectionBuffer.unuse(gpuContext)
        } else {
            GL42.glBindImageTexture(6, reflectionBuffer.getRenderedTexture(0), 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RGBA16F)
            tiledProbeLightingProgram.use()
            tiledProbeLightingProgram.setUniform("N", IMPORTANCE_SAMPLE_COUNT)
            tiledProbeLightingProgram.setUniform("useAmbientOcclusion", engineContext.config.quality.isUseAmbientOcclusion)
            tiledProbeLightingProgram.setUniform("useSSR", engineContext.config.quality.isUseSSR)
            tiledProbeLightingProgram.setUniform("screenWidth", engineContext.config.width.toFloat())
            tiledProbeLightingProgram.setUniform("screenHeight", engineContext.config.height.toFloat())
            tiledProbeLightingProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
            tiledProbeLightingProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
            tiledProbeLightingProgram.setUniform("activeProbeCount", renderState.environmentProbesState.activeProbeCount)
            bindEnvironmentProbePositions(tiledProbeLightingProgram, renderState.environmentProbesState)
            tiledProbeLightingProgram.dispatchCompute(reflectionBuffer.width / 16, reflectionBuffer.height / 16, 1) //16+1
            //		GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
        }

        GL11.glDeleteTextures(copyTextureId)
    }

    companion object {
        private val LOGGER = getLogger()

        @Volatile
        var USE_COMPUTESHADER_FOR_REFLECTIONS = false
        @Volatile
        var IMPORTANCE_SAMPLE_COUNT = 8
    }
}

class LineRendererImpl(engineContext: EngineContext<OpenGl>) : LineRenderer {

    private val programManager: ProgramManager<OpenGl> = engineContext.programManager
    private val linePoints = ArrayList<Vector3f>()
    private val linesProgram = programManager.getProgramFromFileNames("mvp_vertex.glsl", "simple_color_fragment.glsl")

    private val buffer = VertexBuffer(engineContext.gpuContext, EnumSet.of(DataChannels.POSITION3), floatArrayOf(0f, 0f, 0f, 0f)).apply {
        upload()
    }

    override fun batchLine(from: Vector3f, to: Vector3f) {
        linePoints.add(from)
        linePoints.add(to)
    }

    override fun batchPointForLine(point: Vector3f) {
        linePoints.add(point)
    }

    override fun drawAllLines(lineWidth: Float, action: Consumer<Program>) {
        linesProgram.use()
        action.accept(linesProgram)
        drawLines(linesProgram, lineWidth)
        linePoints.clear()
    }

    override fun drawLines(program: Program, lineWidth: Float): Int {
        val points = FloatArray(linePoints.size * 3)
        for (i in linePoints.indices) {
            val point = linePoints[i]
            points[3 * i] = point.x
            points[3 * i + 1] = point.y
            points[3 * i + 2] = point.z
        }
        buffer.putValues(*points)
        buffer.upload().join()
        buffer.drawDebugLines(lineWidth)
        glFinish()
        linePoints.clear()
        return points.size / 3 / 2
    }
}

fun LineRenderer.batchAABBLines(minWorld: Vector3f, maxWorld: Vector3f) {
    run {
        val min = Vector3f(minWorld.x(), minWorld.y(), minWorld.z())
        val max = Vector3f(minWorld.x(), minWorld.y(), maxWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(minWorld.x(), minWorld.y(), minWorld.z())
        val max = Vector3f(minWorld.x(), maxWorld.y(), minWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(minWorld.x(), minWorld.y(), minWorld.z())
        val max = Vector3f(maxWorld.x(), minWorld.y(), minWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(minWorld.x(), maxWorld.y(), minWorld.z())
        val max = Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(minWorld.x(), maxWorld.y(), minWorld.z())
        val max = Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z())
        batchLine(min, max)
    }


    run {
        val min = Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z())
        val max = Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z())
        val max = Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z())
        val max = Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(minWorld.x(), minWorld.y(), maxWorld.z())
        val max = Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(maxWorld.x(), minWorld.y(), minWorld.z())
        val max = Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z())
        val max = Vector3f(maxWorld.x(), minWorld.y(), minWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z())
        val max = Vector3f(minWorld.x(), minWorld.y(), maxWorld.z())
        batchLine(min, max)
    }
}
