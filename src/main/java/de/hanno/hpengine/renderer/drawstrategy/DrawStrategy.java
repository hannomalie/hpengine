package de.hanno.hpengine.renderer.drawstrategy;

import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.engine.PerEntityInfo;
import de.hanno.hpengine.renderer.OpenGLContext;
import de.hanno.hpengine.renderer.RenderState;
import de.hanno.hpengine.renderer.constants.GlCap;
import de.hanno.hpengine.renderer.material.Material;
import de.hanno.hpengine.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.shader.Program;

public interface DrawStrategy {

    static int draw(RenderState renderState, PerEntityInfo perEntityInfo) {
        return draw(renderState, perEntityInfo, perEntityInfo.getProgram());
    }
    static int draw(RenderState renderState, PerEntityInfo perEntityInfo, Program program) {
        if(!perEntityInfo.isVisible() || !perEntityInfo.isVisibleForCamera()) {
            return 0;
        }

        if (perEntityInfo.getProgram() == null) {
            return 0;
        }
        Program currentProgram = program;
        currentProgram.setUniform("entityIndex", perEntityInfo.getEntityBufferIndex());
        currentProgram.setUniform("indirect", false);


//        if(material.getMaterialType().equals(Material.MaterialType.FOLIAGE))
        {
            OpenGLContext.getInstance().disable(GlCap.CULL_FACE);
        }

        if (Config.DRAWLINES_ENABLED) {
            return renderState.getVertexBuffer()
                    .drawLinesInstancedBaseVertex(renderState.getIndexBuffer(), perEntityInfo.getIndexCount(), perEntityInfo.getInstanceCount(), perEntityInfo.getIndexOffset(), perEntityInfo.getBaseVertex());
        } else {return renderState.getVertexBuffer()
                .drawInstancedBaseVertex(renderState.getIndexBuffer(), perEntityInfo.getIndexCount(), perEntityInfo.getInstanceCount(), perEntityInfo.getIndexOffset(), perEntityInfo.getBaseVertex());
        }
    }

    default void draw(DrawResult result, RenderState renderState) {
        draw(result, null, renderState);
    }

    void draw(DrawResult result, RenderTarget renderTarget, RenderState renderState);
}
