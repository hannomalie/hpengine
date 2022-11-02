package de.hanno.hpengine.graphics.renderer

import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.graphics.renderer.drawstrategy.RenderingMode
import de.hanno.hpengine.graphics.renderer.pipelines.CommandOrganizationGpuCulled
import de.hanno.hpengine.graphics.renderer.pipelines.FirstPassUniforms
import de.hanno.hpengine.graphics.shader.IProgram
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.scene.VertexIndexBuffer

class DirectDrawDescription<T : FirstPassUniforms>(
    val renderState: RenderState,
    val renderBatches: List<RenderBatch>,
    val program: IProgram<T>,
    val vertexIndexBuffer: VertexIndexBuffer,
    val beforeDraw: (RenderState, IProgram<T>, Camera) -> Unit,
    val mode: RenderingMode,
    val drawCam: Camera,
    val cullCam: Camera = drawCam,
    val ignoreCustomPrograms: Boolean,
)

class IndirectCulledDrawDescription<T : FirstPassUniforms>(
    val renderState: RenderState,
    val program: IProgram<T>,
    val commandOrganization: CommandOrganizationGpuCulled,
    val vertexIndexBuffer: VertexIndexBuffer,
    val mode: RenderingMode,
    val camera: Camera,
    val cullCam: Camera = camera,
)