package de.hanno.hpengine.graphics.renderer.deferred.extensions

import Vector4fStruktImpl.Companion.type
import de.hanno.hpengine.camera.CameraComponentsStateHolder

import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.renderer.drawLines
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderExtension
import de.hanno.hpengine.graphics.buffer.TypedGpuBuffer
import de.hanno.hpengine.graphics.buffer.typed
import de.hanno.hpengine.graphics.shader.LinesProgramUniforms
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.ressources.StringBasedCodeSource
import org.joml.Vector3f
import org.koin.core.annotation.Single

@Single(binds = [CameraRenderExtension::class, DeferredRenderExtension::class])
class CameraRenderExtension(
    private val graphicsApi: GraphicsApi,
    renderStateContext: RenderStateContext,
    private val config: Config,
    private val programManager: ProgramManager,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val cameraComponentsStateHolder: CameraComponentsStateHolder,
) : DeferredRenderExtension {

    private val lineVertices: TypedGpuBuffer<Vector4fStrukt> = graphicsApi.PersistentShaderStorageBuffer(24 * Vector4fStrukt.type.sizeInBytes).typed(Vector4fStrukt.type)
    val linesProgram = programManager.run {
        val uniforms = LinesProgramUniforms(graphicsApi)
        getProgram(
            StringBasedCodeSource(
                "mvp_vertex_vec4", """
                //include(globals_structs.glsl)
                
                ${uniforms.shaderDeclarations}

                in vec4 in_Position;

                out vec4 pass_Position;
                out vec4 pass_WorldPosition;

                void main()
                {
                	vec4 vertex = vertices[gl_VertexID];
                	vertex.w = 1;

                	pass_WorldPosition = ${uniforms::modelMatrix.name} * vertex;
                	pass_Position = ${uniforms::projectionMatrix.name} * ${uniforms::viewMatrix.name} * pass_WorldPosition;
                    gl_Position = pass_Position;
                }
            """.trimIndent()
            ),
            StringBasedCodeSource(
                "simple_color_vec3", """
            ${uniforms.shaderDeclarations}

            layout(location=0)out vec4 out_color;

            void main()
            {
                out_color = vec4(${uniforms::color.name},1);
            }
        """.trimIndent()
            ), null, Defines(), uniforms
        )
    }

    override fun renderFirstPass(renderState: RenderState) {
        if (config.debug.isDrawCameras) {
            val camera = renderState[primaryCameraStateHolder.camera]

            graphicsApi.drawLines(
                programManager,
                linesProgram,
                lineVertices,
                renderState[cameraComponentsStateHolder.frustumLines],
                viewMatrix = camera.viewMatrixBuffer,
                projectionMatrix = camera.projectionMatrixBuffer,
                color = Vector3f(1f, 0f, 0f)
            )
        }
    }
}
