package de.hanno.hpengine.engine.scene;

import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.model.DataChannels;
import de.hanno.hpengine.engine.model.IndexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import org.lwjgl.BufferUtils;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

public class VertexIndexBuffer implements Serializable {
    private volatile VertexBuffer vertexBuffer;
    private volatile IndexBuffer indexBuffer;
    private volatile AtomicInteger currentBaseVertex = new AtomicInteger();
    private volatile AtomicInteger currentIndexOffset = new AtomicInteger();

    public VertexIndexBuffer(GpuContext gpuContext, int vertexBufferSizeInFloatsCount, int indexBufferSizeInIntsCount, EnumSet<DataChannels> channels) {
        vertexBuffer = new VertexBuffer(gpuContext, BufferUtils.createFloatBuffer(vertexBufferSizeInFloatsCount), channels);
        indexBuffer  = new IndexBuffer(gpuContext, BufferUtils.createIntBuffer(indexBufferSizeInIntsCount));
    }

    public VertexBuffer getVertexBuffer() {
        return vertexBuffer;
    }

    public void setVertexBuffer(VertexBuffer vertexBuffer) {
        this.vertexBuffer = vertexBuffer;
    }

    public IndexBuffer getIndexBuffer() {
        return indexBuffer;
    }

    public void setIndexBuffer(IndexBuffer indexBuffer) {
        this.indexBuffer = indexBuffer;
    }

    public AtomicInteger getCurrentBaseVertex() {
        return currentBaseVertex;
    }

    public void setCurrentBaseVertex(AtomicInteger currentBaseVertex) {
        this.currentBaseVertex = currentBaseVertex;
    }

    public AtomicInteger getCurrentIndexOffset() {
        return currentIndexOffset;
    }

    public void setCurrentIndexOffset(AtomicInteger currentIndexOffset) {
        this.currentIndexOffset = currentIndexOffset;
    }

    public synchronized VertexIndexOffsets allocate(int elementsCount, int indicesCount) {
        VertexIndexOffsets vertexIndexOffsets = new VertexIndexOffsets(currentBaseVertex.get(), currentIndexOffset.get());
        currentBaseVertex.getAndSet(vertexIndexOffsets.vertexOffset + elementsCount);
        currentIndexOffset.getAndSet(vertexIndexOffsets.indexOffset + indicesCount);
        return vertexIndexOffsets;
    }

    public void resetAllocations() {
        currentBaseVertex.getAndSet(0);
        currentIndexOffset.getAndSet(0);
    }

    public static class VertexIndexOffsets {
        public final int vertexOffset;
        public final int indexOffset;

        public VertexIndexOffsets(int vertexOffset, int indexOffset) {
            this.vertexOffset = vertexOffset;
            this.indexOffset = indexOffset;
        }

    }
}