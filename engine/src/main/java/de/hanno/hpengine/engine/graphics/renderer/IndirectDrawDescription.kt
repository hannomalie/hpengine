package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.PrimitiveMode
import de.hanno.hpengine.engine.graphics.renderer.pipelines.CommandOrganization
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.VertexIndexBuffer

class DirectDrawDescription(
        val renderState: RenderState,
        val renderBatches: List<RenderBatch>,
        val program: Program,
        val vertexIndexBuffer: VertexIndexBuffer,
        val beforeDraw: (RenderState, Program, Camera) -> Unit,
        val mode: PrimitiveMode,
        val drawCam: Camera,
        val cullCam: Camera = drawCam
)

class IndirectDrawDescription(
        val renderState: RenderState,
        val renderBatches: List<RenderBatch>,
        val program: Program,
        val commandOrganization: CommandOrganization,
        val vertexIndexBuffer: VertexIndexBuffer,
        val beforeDraw: (RenderState, Program, Camera) -> Unit,
        val mode: PrimitiveMode,
        val drawCam: Camera,
        val cullCam: Camera = drawCam
)