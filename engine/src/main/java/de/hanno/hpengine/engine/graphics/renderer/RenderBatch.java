package de.hanno.hpengine.engine.graphics.renderer;

import de.hanno.hpengine.engine.model.CommandBuffer.DrawElementsIndirectCommand;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.model.Entity;
import org.lwjgl.util.vector.Vector3f;

import java.util.ArrayList;

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
    private Entity.Update update;
    private DrawElementsIndirectCommand drawElementsIndirectCommand = new DrawElementsIndirectCommand();
    private Vector3f centerWorld;
    private Vector3f minWorldVec3;
    private Vector3f maxWorldVec3;

    public RenderBatch(Program program, int entityBaseIndex, boolean isVisible, boolean isSelected, boolean drawLines, Vector3f cameraWorldPosition, boolean isInReachForTextureStreaming, int instanceCount, boolean visibleForCamera, Entity.Update update, Vector3f minWorld, Vector3f maxWorld, int indexCount, int indexOffset, int baseVertex) {
        init(program, entityBaseIndex, isVisible, isSelected, drawLines, cameraWorldPosition, isInReachForTextureStreaming, instanceCount, visibleForCamera, update, minWorld, maxWorld, getMinWorldVec3(), getMaxWorldVec3(), centerWorld, indexCount, indexOffset, baseVertex);
    }

    public RenderBatch() {
    }

    public void init(Program program, int entityBaseIndex, boolean isVisible, boolean isSelected, boolean drawLines, Vector3f cameraWorldPosition, boolean isInReachForTextureStreaming, int instanceCount, boolean visibleForCamera, Entity.Update update, Vector3f minWorld, Vector3f maxWorld, Vector3f minWorldVec3, Vector3f maxWorldVec3, Vector3f centerWorld, int indexCount, int indexOffset, int baseVertex) {
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
        this.minWorldVec3 = minWorldVec3;
        this.maxWorldVec3 = maxWorldVec3;
        this.centerWorld = centerWorld;
        this.drawElementsIndirectCommand.init(indexCount, instanceCount, indexOffset, baseVertex, 0, entityBaseIndex);
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

    public Entity.Update getUpdate() {
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

    public Vector3f getMinWorldVec3() {
        return minWorldVec3;
    }

    public Vector3f getMaxWorldVec3() {
        return maxWorldVec3;
    }

    public static class RenderBatches extends ArrayList<RenderBatch> {

    }

}