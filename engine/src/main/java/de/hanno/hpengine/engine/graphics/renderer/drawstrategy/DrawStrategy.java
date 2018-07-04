package de.hanno.hpengine.engine.graphics.renderer.drawstrategy;

import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.model.IndexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.graphics.shader.Program;

public interface DrawStrategy {

    static int draw(GpuContext gpuContext, RenderState renderState, RenderBatch renderBatch) {
        return draw(gpuContext, renderState.getVertexIndexBufferStatic().getVertexBuffer(), renderState.getVertexIndexBufferStatic().getIndexBuffer(), renderBatch, renderBatch.getProgram(), !renderBatch.isVisible() || !renderBatch.isVisibleForCamera());
    }
    static int draw(GpuContext gpuContext, VertexBuffer vertexBuffer, IndexBuffer indexBuffer, RenderBatch renderBatch, Program program, boolean invisible) {
        if(invisible) {
            return 0;
        }

        if (program == null) {
            return 0;
        }
        Program currentProgram = program;
        currentProgram.setUniform("entityBaseIndex", 0);
        currentProgram.setUniform("entityIndex", renderBatch.getEntityBufferIndex());
        currentProgram.setUniform("indirect", false);


//        if(material.getMaterialType().equals(SimpleMaterial.MaterialType.FOLIAGE))
        {
            gpuContext.disable(GlCap.CULL_FACE);
        }

        if (Config.getInstance().isDrawLines()) {
            return vertexBuffer.drawLinesInstancedBaseVertex(indexBuffer, renderBatch.getIndexCount(), renderBatch.getInstanceCount(), renderBatch.getIndexOffset(), renderBatch.getBaseVertex());
        } else {
            return vertexBuffer
                .drawInstancedBaseVertex(indexBuffer, renderBatch.getIndexCount(), renderBatch.getInstanceCount(), renderBatch.getIndexOffset(), renderBatch.getBaseVertex());
        }
    }

    default void draw(DrawResult result, RenderState renderState) {
        draw(result, null, renderState);
    }

    void draw(DrawResult result, RenderTarget renderTarget, RenderState renderState);
}
