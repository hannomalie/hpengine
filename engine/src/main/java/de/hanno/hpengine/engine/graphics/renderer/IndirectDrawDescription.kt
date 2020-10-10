package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.PrimitiveMode
import de.hanno.hpengine.engine.graphics.renderer.pipelines.AnimatedFirstPassUniforms
import de.hanno.hpengine.engine.graphics.renderer.pipelines.CommandOrganization
import de.hanno.hpengine.engine.graphics.renderer.pipelines.FirstPassUniforms
import de.hanno.hpengine.engine.graphics.renderer.pipelines.StaticFirstPassUniforms
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.VertexIndexBuffer

class DirectDrawDescription<T: FirstPassUniforms>(
        val renderState: RenderState,
        val renderBatches: List<RenderBatch>,
        val program: Program<T>,
        val vertexIndexBuffer: VertexIndexBuffer,
        val beforeDraw: (RenderState, Program<T>, Camera) -> Unit,
        val mode: PrimitiveMode,
        val drawCam: Camera,
        val cullCam: Camera = drawCam
)

class IndirectDrawDescription<T: FirstPassUniforms>(
        val renderState: RenderState,
        val renderBatches: List<RenderBatch>,
        val program: Program<T>,
        val commandOrganization: CommandOrganization,
        val vertexIndexBuffer: VertexIndexBuffer,
        val beforeDraw: (RenderState, Program<T>, Camera) -> Unit,
        val mode: PrimitiveMode,
        val drawCam: Camera,
        val cullCam: Camera = drawCam
)