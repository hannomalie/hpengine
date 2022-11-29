package de.hanno.hpengine.camera

import Vector4fStruktImpl.Companion.type
import com.artemis.World
import com.artemis.hackedOutComponents

import de.hanno.hpengine.artemis.CameraComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.RenderStateContext
import de.hanno.hpengine.graphics.renderer.drawLines
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentMappedBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.graphics.shader.LinesProgramUniforms
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.ressources.StringBasedCodeSource
import org.joml.Vector3f
import org.joml.Vector3fc

context(GpuContext, RenderStateContext)
class CameraRenderExtension(
    val config: Config,
    val programManager: ProgramManager
) : DeferredRenderExtension {

    private val frustumLines = renderState.registerState { mutableListOf<Vector3fc>() }
    private val lineVertices = PersistentMappedBuffer(24 * Vector4fStrukt.type.sizeInBytes).typed(Vector4fStrukt.type)
    val linesProgram = programManager.run {
        val uniforms = LinesProgramUniforms()
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

    override fun renderFirstPass(
        renderState: RenderState
    ) {
        if (config.debug.isDrawCameras) {
            drawLines(
                programManager,
                linesProgram,
                lineVertices,
                renderState[frustumLines],
                viewMatrix = renderState.camera.viewMatrixAsBuffer,
                projectionMatrix = renderState.camera.projectionMatrixAsBuffer,
                color = Vector3f(1f, 0f, 0f)
            )
        }
    }

    override fun extract(renderState: RenderState, world: World) {
        if (config.debug.isDrawCameras) {
            renderState[frustumLines].apply {
                clear()
                val components = world.getMapper(CameraComponent::class.java).hackedOutComponents
                components.indices.forEach { i ->
                    // TODO: cache frustum somehow for camera components
//                    val corners = components[i].frustumCorners
//
//                    addLine(corners[0], corners[1])
//                    addLine(corners[1], corners[2])
//                    addLine(corners[2], corners[3])
//                    addLine(corners[3], corners[0])
//
//                    addLine(corners[4], corners[5])
//                    addLine(corners[5], corners[6])
//                    addLine(corners[6], corners[7])
//                    addLine(corners[7], corners[4])
//
//                    addLine(corners[0], corners[6])
//                    addLine(corners[1], corners[7])
//                    addLine(corners[2], corners[4])
//                    addLine(corners[3], corners[5])
                }
            }
        }

    }
}
