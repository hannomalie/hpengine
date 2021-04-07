package de.hanno.hpengine.engine.graphics.renderer.environmentsampler

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.GpuContext.Companion.exitOnGLError
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.DepthBuffer.Companion.invoke
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer.Companion.invoke
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget.Companion.invoke
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.toTextures
import de.hanno.hpengine.engine.graphics.shader.ComputeProgram
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.Uniforms
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.EnvironmentProbe
import de.hanno.hpengine.engine.scene.EnvironmentProbeSystem
import de.hanno.hpengine.engine.transform.Spatial.Companion.isInFrustum
import de.hanno.hpengine.engine.vertexbuffer.QuadVertexBuffer
import de.hanno.hpengine.engine.vertexbuffer.VertexBuffer
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL43
import java.nio.FloatBuffer
import java.util.HashSet

class EnvironmentSampler(val entity: Entity,
                         probe: EnvironmentProbe,
                         width: Int, height: Int, probeIndex: Int,
                         private val environmentProbeSystem: EnvironmentProbeSystem,
                         programManager: ProgramManager<OpenGl>,
                         config: Config,
                         textureManager: TextureManager) {
    val cubeMapProgram: Program<Uniforms>
    private val cubeMapLightingProgram: Program<Uniforms>
    private val depthPrePassProgram: Program<Uniforms>
    val tiledProbeLightingProgram: ComputeProgram
    val cubemapRadianceProgram: ComputeProgram
    val cubemapRadianceFragmentProgram: Program<Uniforms>
    private val entityBuffer = BufferUtils.createFloatBuffer(16)

    var drawnOnce = false

    var sidesDrawn: MutableSet<Int> = HashSet()

    val probe: EnvironmentProbe
    val fullscreenBuffer: VertexBuffer
    val cubeMapView: Int
    val cubeMapView1: Int
    val cubeMapView2: Int
    val cubeMapFaceViews = Array(4) { IntArray(6) }
    private val secondPassPointProgram: Program<Uniforms>
    private val secondPassTubeProgram: Program<Uniforms>
    private val secondPassAreaProgram: Program<Uniforms>
    val secondPassDirectionalProgram: Program<Uniforms>
    val firstPassDefaultProgram: Program<Uniforms>
    val renderTarget: RenderTarget<Texture2D>
    val camera: Camera

    var gpuContext: GpuContext<OpenGl>
    val config: Config
    val textureManager: TextureManager

    fun registerSideAsDrawn(i: Int) {
        sidesDrawn.add(i)
        if (sidesDrawn.size == 6) {
            drawnOnce = true
        }
    }

    fun resetDrawing() {
        sidesDrawn.clear()
        drawnOnce = false
    }

    fun drawEntities(renderState: RenderState, program: Program<Uniforms>,
                     viewMatrixAsBuffer: FloatBuffer, projectionMatrixAsBuffer: FloatBuffer,
                     viewProjectionMatrixAsBuffer: FloatBuffer) {
        program.use()
        bindShaderSpecificsPerCubeMapSide(renderState, viewMatrixAsBuffer, projectionMatrixAsBuffer, viewProjectionMatrixAsBuffer, program)
        for (e in renderState.renderBatchesStatic) {
            if (!isInFrustum(camera, e.centerWorld, e.entityMinWorld, e.entityMaxWorld)) {
//				continue;
            }
            renderState.vertexIndexBufferStatic.indexBuffer.draw(e, program)
        }
    }

    private fun bindShaderSpecificsPerCubeMapSide(renderState: RenderState,
                                                  viewMatrixAsBuffer: FloatBuffer,
                                                  projectionMatrixAsBuffer: FloatBuffer,
                                                  viewProjectionMatrixAsBuffer: FloatBuffer,
                                                  program: Program<Uniforms>) {
        val light = renderState.directionalLightState
//        TODO: Reimplement
//        program.setUniform("lightDirection", light!!.entity.transform.viewDirection)
//        program.setUniform("lightDiffuse", light.color)
//        program.setUniform("lightAmbient", config.effects.ambientLight)
//        program.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer)
//        program.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer)
//        program.setUniformAsMatrix4("viewProjectionMatrix", viewProjectionMatrixAsBuffer)
//        program.setUniformAsMatrix4("shadowMatrix", light.viewProjectionMatrixAsBuffer)
    }


    fun rotateForIndex(i: Int, camera: Entity) {
        val deltaNear = 0.0f
        val deltaFar = 100.0f
        val halfSizeX = probe.size.x / 2
        val halfSizeY = probe.size.y / 2
        val halfSizeZ = probe.size.z / 2
        val position = camera.transform.position
        when (i) {
            0 -> {
                camera.transform.rotation(Quaternionf().identity())
                camera.transform.rotate(AxisAngle4f(0f, 0f, 1f, Math.toRadians(180.0).toFloat()))
                camera.transform.rotate(AxisAngle4f(0f, 1f, 0f, Math.toRadians(-90.0).toFloat()))
                //			probe.getCamera().setNear(0 + halfSizeX*deltaNear);
                probe.camera.far = halfSizeX * deltaFar
            }
            1 -> {
                camera.transform.rotation(Quaternionf().identity())
                camera.transform.rotate(AxisAngle4f(0f, 0f, 1f, Math.toRadians(180.0).toFloat()))
                camera.transform.rotate(AxisAngle4f(0f, 1f, 0f, Math.toRadians(90.0).toFloat()))
                //			probe.getCamera().setNear(0 + halfSizeX*deltaNear);
                probe.camera.far = halfSizeX * deltaFar
            }
            2 -> {
                camera.transform.rotation(Quaternionf().identity())
                camera.transform.rotate(AxisAngle4f(0f, 0f, 1f, Math.toRadians(180.0).toFloat()))
                camera.transform.rotate(AxisAngle4f(1f, 0f, 0f, Math.toRadians(90.0).toFloat()))
                camera.transform.rotate(AxisAngle4f(0f, 1f, 0f, Math.toRadians(180.0).toFloat()))
                //			probe.getCamera().setNear(0 + halfSizeY*deltaNear);
                probe.camera.far = halfSizeY * deltaFar
            }
            3 -> {
                camera.transform.rotation(Quaternionf().identity())
                camera.transform.rotate(AxisAngle4f(0f, 0f, 1f, Math.toRadians(180.0).toFloat()))
                camera.transform.rotate(AxisAngle4f(1f, 0f, 0f, Math.toRadians(-90.0).toFloat()))
                //			probe.getCamera().setNear(0 + halfSizeY*deltaNear);
                probe.camera.far = halfSizeY * deltaFar
            }
            4 -> {
                camera.transform.rotation(Quaternionf().identity())
                camera.transform.rotate(AxisAngle4f(0f, 0f, 1f, Math.toRadians(180.0).toFloat()))
                camera.transform.rotate(AxisAngle4f(0f, 1f, 0f, Math.toRadians(-180.0).toFloat()))
                //			probe.getCamera().setNear(0 + halfSizeZ*deltaNear);
                probe.camera.far = halfSizeZ * deltaFar
            }
            5 -> {
                camera.transform.rotation(Quaternionf().identity())
                camera.transform.rotate(AxisAngle4f(0f, 0f, 1f, Math.toRadians(180.0).toFloat()))
                //			de.hanno.hpengine.camera.rotateWorld(new Vector4f(0, 1, 0, 180));
//			probe.getCamera().setNear(0 + halfSizeZ*deltaNear);
                probe.camera.far = halfSizeZ * deltaFar
            }
            else -> {
            }
        }
    }

    init {
        gpuContext = programManager.gpuContext
        this.config = config
        this.textureManager = textureManager
        val camera = Camera(entity, near = 0.1f, far = 5000f, fov = 90f, ratio = 1f)
        entity.addComponent(camera)
        camera.width = width.toFloat()
        camera.width = height.toFloat()
        this.camera = camera
        this.probe = probe
        val cubeMapCamInitialOrientation = Quaternionf().identity()
        entity.transform.rotate(cubeMapCamInitialOrientation)

        cubeMapProgram = config.run { programManager.getProgram(
            EngineAsset("shaders/first_pass_vertex.glsl").toCodeSource(),
            EngineAsset("shaders/cubemap_fragment.glsl").toCodeSource())
        }

        depthPrePassProgram = config.run { programManager.getProgram(
            EngineAsset("shaders/first_pass_vertex.glsl").toCodeSource(),
            EngineAsset("shaders/cubemap_fragment.glsl").toCodeSource())
        }

        cubeMapLightingProgram = config.run { programManager.getProgram(
            EngineAsset("shaders/first_pass_vertex.glsl").toCodeSource(),
            EngineAsset("shaders/cubemap_lighting_fragment.glsl").toCodeSource())
        }

        tiledProbeLightingProgram = programManager.getComputeProgram(config.EngineAsset("shaders/tiled_probe_lighting_probe_rendering_compute.glsl"))
        cubemapRadianceProgram = programManager.getComputeProgram(config.EngineAsset("shaders/cubemap_radiance_compute.glsl"))

        cubemapRadianceFragmentProgram = config.run { programManager.getProgram(
                config.EngineAsset("shaders/passthrough_vertex.glsl").toCodeSource(),
                config.EngineAsset("shaders/cubemap_radiance_fragment.glsl").toCodeSource()) }

        secondPassPointProgram = config.run {
            programManager.getProgram(
                EngineAsset("shaders/second_pass_point_vertex.glsl").toCodeSource(),
                EngineAsset("shaders/scond_pass_point_fragment.glsl").toCodeSource()
            )
        }
        secondPassTubeProgram = config.run {
            programManager.getProgram(
                EngineAsset("shaders/second_pass_point_vertex.glsl").toCodeSource(),
                EngineAsset("shaders/scond_pass_tube_fragment.glsl").toCodeSource()
            )
        }
        secondPassAreaProgram = config.run {
            programManager.getProgram(
                EngineAsset("shaders/second_pass_area_vertex.glsl").toCodeSource(),
                EngineAsset("shaders/scond_pass_area_fragment.glsl").toCodeSource()
            )
        }
        secondPassDirectionalProgram = config.run {
            programManager.getProgram(
                EngineAsset("shaders/second_pass_directional_vertex.glsl").toCodeSource(),
                EngineAsset("shaders/scond_pass_directional_fragment.glsl").toCodeSource()
            )
        }
        firstPassDefaultProgram = config.run {
            programManager.getProgram(
                EngineAsset("shaders/first_pass_vertex.glsl").toCodeSource(),
                EngineAsset("shaders/frst_pass_fragment.glsl").toCodeSource()
            )
        }
        val cubeMapArrayRenderTarget = environmentProbeSystem.cubeMapArrayRenderTarget
        cubeMapView = GL11.glGenTextures()
        cubeMapView1 = GL11.glGenTextures()
        cubeMapView2 = GL11.glGenTextures()
        exitOnGLError("EnvironmentSampler before view creation")
        val diffuseInternalFormat = cubeMapArrayRenderTarget.getCubeMapArray(3).internalFormat
        for (z in 0..5) {
            cubeMapFaceViews[0][z] = GL11.glGenTextures()
            cubeMapFaceViews[1][z] = GL11.glGenTextures()
            cubeMapFaceViews[2][z] = GL11.glGenTextures()
            cubeMapFaceViews[3][z] = GL11.glGenTextures()
            //GL43.glTextureView(cubeMapFaceViews[i][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(i).getTextureId(), cubeMapArrayRenderTarget.getCubeMapArray(i).getInternalFormat(), 0, 1, 6 * probe.getIndex() + z, 1);
            GL43.glTextureView(cubeMapFaceViews[0][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(0).id, cubeMapArrayRenderTarget.getCubeMapArray(0).internalFormat, 0, 1, 6 * probeIndex + z, 1)
            GL43.glTextureView(cubeMapFaceViews[1][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(1).id, cubeMapArrayRenderTarget.getCubeMapArray(1).internalFormat, 0, 1, 6 * probeIndex + z, 1)
            GL43.glTextureView(cubeMapFaceViews[2][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(2).id, cubeMapArrayRenderTarget.getCubeMapArray(2).internalFormat, 0, 1, 6 * probeIndex + z, 1)
            GL43.glTextureView(cubeMapFaceViews[3][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(3).id, diffuseInternalFormat, 0, 1, 6 * probeIndex + z, 1)
        }
        GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(0).id, cubeMapArrayRenderTarget.getCubeMapArray(0).internalFormat, 0, EnvironmentProbeSystem.CUBEMAP_MIPMAP_COUNT, 6 * probeIndex, 6)
        GL43.glTextureView(cubeMapView1, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(1).id, cubeMapArrayRenderTarget.getCubeMapArray(1).internalFormat, 0, EnvironmentProbeSystem.CUBEMAP_MIPMAP_COUNT, 6 * probeIndex, 6)
        GL43.glTextureView(cubeMapView2, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(2).id, cubeMapArrayRenderTarget.getCubeMapArray(2).internalFormat, 0, EnvironmentProbeSystem.CUBEMAP_MIPMAP_COUNT, 6 * probeIndex, 6)
        renderTarget = invoke(
                gpuContext,
                invoke(gpuContext, invoke(gpuContext, EnvironmentProbeSystem.RESOLUTION, EnvironmentProbeSystem.RESOLUTION)),
                EnvironmentProbeSystem.RESOLUTION, EnvironmentProbeSystem.RESOLUTION,
                listOf(ColorAttachmentDefinition("Environment Diffuse", diffuseInternalFormat)).toTextures(gpuContext, EnvironmentProbeSystem.RESOLUTION, EnvironmentProbeSystem.RESOLUTION),
                "Environment Sampler"
        )
        fullscreenBuffer = QuadVertexBuffer(gpuContext, true)
        fullscreenBuffer.upload()
        exitOnGLError("EnvironmentSampler constructor")
    }
}