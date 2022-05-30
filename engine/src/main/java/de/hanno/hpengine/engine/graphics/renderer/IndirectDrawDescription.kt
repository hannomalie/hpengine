package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.RenderingMode
import de.hanno.hpengine.engine.graphics.renderer.pipelines.CommandOrganization
import de.hanno.hpengine.engine.graphics.renderer.pipelines.CommandOrganizationGpuCulled
import de.hanno.hpengine.engine.graphics.renderer.pipelines.FirstPassUniforms
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.VertexIndexBuffer

class DirectDrawDescription<T : FirstPassUniforms>(
    val renderState: RenderState,
    val renderBatches: List<RenderBatch>,
    val program: Program<T>,
    val vertexIndexBuffer: VertexIndexBuffer,
    val beforeDraw: (RenderState, Program<T>, Camera) -> Unit,
    val mode: RenderingMode,
    val drawCam: Camera,
    val cullCam: Camera = drawCam
)

class IndirectDrawDescription<T : FirstPassUniforms>(
    val renderState: RenderState,
    val program: Program<T>,
    val commandOrganization: CommandOrganization,
    val vertexIndexBuffer: VertexIndexBuffer,
    val beforeDraw: (RenderState, Program<T>, Camera) -> Unit,
    val mode: RenderingMode,
    val drawCam: Camera,
    val cullCam: Camera = drawCam
)

class IndirectCulledDrawDescription<T : FirstPassUniforms>(
    val renderState: RenderState,
    val program: Program<T>,
    val commandOrganization: CommandOrganizationGpuCulled,
    val vertexIndexBuffer: VertexIndexBuffer,
    val beforeDraw: (RenderState, Program<T>, Camera) -> Unit,
    val mode: RenderingMode,
    val drawCam: Camera,
    val cullCam: Camera = drawCam
)