package renderer.drawstrategy;

import engine.PerEntityInfo;
import renderer.OpenGLContext;
import renderer.RenderExtract;
import renderer.constants.GlCap;
import renderer.material.Material;
import renderer.rendertarget.RenderTarget;
import shader.Program;

import javax.annotation.Nullable;

public interface DrawStrategy {

    static int draw(PerEntityInfo perEntityInfo) {
        if(!perEntityInfo.isVisible() || !perEntityInfo.isVisibleForCamera()) {
            return 0;
        }

        if (perEntityInfo.getProgram() == null) {
            return 0;
        }
        Program currentProgram = perEntityInfo.getProgram();
        currentProgram.setUniform("entityIndex", perEntityInfo.getEntityIndex());
        currentProgram.setUniform("entityBaseIndex", perEntityInfo.getEntityBaseIndex());

        Material material = perEntityInfo.getMaterial();
        material.setTexturesActive(currentProgram, perEntityInfo.isInReachForTextureLoading());

        if(material.getMaterialType().equals(Material.MaterialType.FOLIAGE)) {
            OpenGLContext.getInstance().disable(GlCap.CULL_FACE);
        }
        if (perEntityInfo.isDrawLines()) {
            return perEntityInfo.getVertexBuffer().drawDebug(2, 0);//ModelLod.ModelLodStrategy.DISTANCE_BASED.getIndexBufferIndex(perEntityInfo.getExtract(), this));
        } else {
            return perEntityInfo.getVertexBuffer().drawInstanced(perEntityInfo.getInstanceCount());
        }
    }

    default DrawResult draw(RenderExtract renderExtract) {
            return draw(null, renderExtract);
    }

    DrawResult draw(@Nullable RenderTarget renderTarget, RenderExtract renderExtract);
}
