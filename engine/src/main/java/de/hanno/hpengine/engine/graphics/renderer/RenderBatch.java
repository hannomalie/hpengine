package de.hanno.hpengine.engine.graphics.renderer;

import de.hanno.hpengine.engine.model.CommandBuffer.DrawElementsIndirectCommand;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.model.Update;
import de.hanno.hpengine.engine.model.material.MaterialInfo;
import de.hanno.hpengine.engine.transform.AABB;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class RenderBatch {
    private Program program;
    private boolean isVisible;
    private boolean isSelected;
    private boolean drawLines;
    private Vector3f minWorld;
    private Vector3f maxWorld;
    private Vector3f cameraWorldPosition;
    private boolean isInReachForTextureStreaming;
    private boolean visibleForCamera;
    private Update update;
    private DrawElementsIndirectCommand drawElementsIndirectCommand = new DrawElementsIndirectCommand();
    private Vector3f centerWorld;
    private boolean animated;
    private float boundingSphereRadius;
    private List<AABB> instanceMinMaxWorlds = new ArrayList();
    private MaterialInfo materialInfo;

    public RenderBatch() {
    }

    public RenderBatch init(Program program, int entityBaseIndex, boolean isVisible, boolean isSelected, boolean drawLines, Vector3f cameraWorldPosition, boolean isInReachForTextureStreaming, int instanceCount, boolean visibleForCamera, Update update, Vector3f minWorld, Vector3f maxWorld, Vector3f centerWorld, float boundingSphereRadius, int indexCount, int indexOffset, int baseVertex, boolean animated, List<AABB> instanceMinMaxWorlds, MaterialInfo materialInfo) {
        this.program = program;
        this.isVisible = isVisible;
        this.isSelected = isSelected;
        this.drawLines = drawLines;
        this.cameraWorldPosition = cameraWorldPosition;
        this.isInReachForTextureStreaming = isInReachForTextureStreaming;
        this.visibleForCamera = visibleForCamera;
        this.update = update;
        this.minWorld = minWorld;
        this.maxWorld = maxWorld;
        this.instanceMinMaxWorlds.clear();
        this.instanceMinMaxWorlds.addAll(instanceMinMaxWorlds);
        this.boundingSphereRadius = boundingSphereRadius;
        this.centerWorld = centerWorld;
        this.drawElementsIndirectCommand.init(indexCount, instanceCount, indexOffset, baseVertex, 0, entityBaseIndex);
        this.animated = animated;
        this.materialInfo = materialInfo;
        return this;
    }

    public Program getProgram() {
        return program;
    }

    public int getEntityBufferIndex() {
        return drawElementsIndirectCommand.entityOffset;
    }

    public boolean isVisible() {
        return isVisible;
    }
    public boolean isVisibleForCamera() {
        return visibleForCamera;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public boolean isDrawLines() {
        return drawLines;
    }

    public Vector3f getCameraWorldPosition() {
        return cameraWorldPosition;
    }

    public boolean isInReachForTextureLoading() {
        return isInReachForTextureStreaming;
    }

    public int getInstanceCount() {
        return drawElementsIndirectCommand.primCount;
    }

    public Update getUpdate() {
        return update;
    }

    public Vector3f getMinWorld() {
        return minWorld;
    }

    public Vector3f getMaxWorld() {
        return maxWorld;
    }

    public boolean isInReachForTextureStreaming() {
        return isInReachForTextureStreaming;
    }


    public int getIndexCount() {
        return drawElementsIndirectCommand.count;
    }

    public int getIndexOffset() {
        return drawElementsIndirectCommand.firstIndex;
    }

    public int getBaseVertex() {
        return drawElementsIndirectCommand.baseVertex;
    }

    public DrawElementsIndirectCommand getDrawElementsIndirectCommand() {
        return drawElementsIndirectCommand;
    }

    public int getVertexCount() {
        return drawElementsIndirectCommand.count / 3;
    }

    public Vector3f getCenterWorld() {
        return centerWorld;
    }

    public boolean isStatic() {
        return !animated;
    }

    public float getBoundingSphereRadius() {
        return boundingSphereRadius;
    }

    public List<AABB> getInstanceMinMaxWorlds() {
        return instanceMinMaxWorlds;
    }

    public static class RenderBatches extends ArrayList<RenderBatch> {

    }

    public MaterialInfo getMaterialInfo() {
        return materialInfo;
    }
}