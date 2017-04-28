package de.hanno.hpengine.renderer.drawstrategy;

import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.engine.PerMeshInfo;
import de.hanno.hpengine.engine.model.IndexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.renderer.GraphicsContext;
import de.hanno.hpengine.renderer.state.RenderState;
import de.hanno.hpengine.renderer.constants.GlCap;
import de.hanno.hpengine.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.shader.Program;

public interface DrawStrategy {

    static int draw(RenderState renderState, PerMeshInfo perMeshInfo) {
        return draw(renderState.getVertexIndexBuffer().getVertexBuffer(), renderState.getVertexIndexBuffer().getIndexBuffer(), perMeshInfo, perMeshInfo.getProgram(), !perMeshInfo.isVisible() || !perMeshInfo.isVisibleForCamera());
    }
    static int draw(VertexBuffer vertexBuffer, IndexBuffer indexBuffer, PerMeshInfo perMeshInfo, Program program, boolean invisible) {
        if(invisible) {
            return 0;
        }

        if (program == null) {
            return 0;
        }
        Program currentProgram = program;
        currentProgram.setUniform("entityIndex", perMeshInfo.getEntityBufferIndex());
        currentProgram.setUniform("indirect", false);


//        if(material.getMaterialType().equals(Material.MaterialType.FOLIAGE))
        {
            GraphicsContext.getInstance().disable(GlCap.CULL_FACE);
        }

        if (Config.getInstance().isDrawLines()) {
            return vertexBuffer.drawLinesInstancedBaseVertex(indexBuffer, perMeshInfo.getIndexCount(), perMeshInfo.getInstanceCount(), perMeshInfo.getIndexOffset(), perMeshInfo.getBaseVertex());
        } else {
            return vertexBuffer
                .drawInstancedBaseVertex(indexBuffer, perMeshInfo.getIndexCount(), perMeshInfo.getInstanceCount(), perMeshInfo.getIndexOffset(), perMeshInfo.getBaseVertex());
        }
    }

    default void draw(DrawResult result, RenderState renderState) {
        draw(result, null, renderState);
    }

    void draw(DrawResult result, RenderTarget renderTarget, RenderState renderState);
}
