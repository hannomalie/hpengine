package de.hanno.hpengine.renderer.drawstrategy;

import de.hanno.hpengine.component.ModelComponent;
import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.engine.PerEntityInfo;
import de.hanno.hpengine.renderer.OpenGLContext;
import de.hanno.hpengine.renderer.RenderState;
import de.hanno.hpengine.renderer.constants.GlCap;
import de.hanno.hpengine.renderer.material.Material;
import de.hanno.hpengine.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.shader.Program;

import javax.annotation.Nullable;

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

        Material material = perEntityInfo.getMaterial();
        material.setTexturesActive(currentProgram, perEntityInfo.isInReachForTextureLoading());

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

    default DrawResult draw(RenderState renderState) {
        return draw(null, renderState);
    }

    DrawResult draw(@Nullable RenderTarget renderTarget, RenderState renderState);
}