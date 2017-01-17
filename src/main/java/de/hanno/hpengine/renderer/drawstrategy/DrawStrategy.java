package de.hanno.hpengine.renderer.drawstrategy;

import de.hanno.hpengine.component.ModelComponent;
import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.engine.PerEntityInfo;
import de.hanno.hpengine.renderer.OpenGLContext;
import de.hanno.hpengine.renderer.RenderExtract;
import de.hanno.hpengine.renderer.constants.GlCap;
import de.hanno.hpengine.renderer.material.Material;
import de.hanno.hpengine.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.shader.Program;

import javax.annotation.Nullable;

public interface DrawStrategy {

    static int draw(PerEntityInfo perEntityInfo) {
        return draw(perEntityInfo, perEntityInfo.getProgram());
    }
    static int draw(PerEntityInfo perEntityInfo, Program program) {
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
            return ModelComponent.getGlobalVertexBuffer()
                    .drawLinesInstancedBaseVertex(ModelComponent.getGlobalIndexBuffer(), perEntityInfo.getIndexCount(), perEntityInfo.getInstanceCount(), perEntityInfo.getIndexOffset(), perEntityInfo.getBaseVertex());
        } else {return ModelComponent.getGlobalVertexBuffer()
                .drawInstancedBaseVertex(ModelComponent.getGlobalIndexBuffer(), perEntityInfo.getIndexCount(), perEntityInfo.getInstanceCount(), perEntityInfo.getIndexOffset(), perEntityInfo.getBaseVertex());
        }
    }

    default DrawResult draw(RenderExtract renderExtract) {
        return draw(null, renderExtract);
    }

    DrawResult draw(@Nullable RenderTarget renderTarget, RenderExtract renderExtract);
}
