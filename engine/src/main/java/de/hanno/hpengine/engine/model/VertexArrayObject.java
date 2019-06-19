package de.hanno.hpengine.engine.model;

import de.hanno.hpengine.engine.graphics.GpuContext;
import org.lwjgl.opengl.*;

import java.util.*;

public class VertexArrayObject {

    private final EnumSet<DataChannels> channels;
    private final GpuContext gpuContext;
    private int id = -1;
    private transient boolean attributesSetUp = false;


    public static VertexArrayObject getForChannels(GpuContext gpuContext, EnumSet<DataChannels> channels) {
        return new VertexArrayObject(gpuContext, channels);
    }

    private VertexArrayObject(GpuContext gpuContext, EnumSet<DataChannels> channels) {
        this.gpuContext = gpuContext;
        this.channels = channels.clone();
        gpuContext.execute("VertexArrayObject", () -> setId(GL30.glGenVertexArrays()));
        setUpAttributes();
    }

    private void unbind() {
        gpuContext.execute("VertexArrayObject.unbind", () -> GL30.glBindVertexArray(0));
    }

    public void bind() {
//        if(CURRENTLY_BOUND_VAO == id) { return; }
        gpuContext.execute("VertexArrayObject.bind", bindRunnable);
    }

    private Runnable bindRunnable = () -> {
        if (getId() <= 0) {
            setId(GL30.glGenVertexArrays());
        }
        GL30.glBindVertexArray(getId());
    };

    private void setUpAttributes() {
        if(attributesSetUp) { return; }
        gpuContext.execute("VertexArrayObject.setUpAttributes", () -> {
            bind();
            int currentOffset = 0;
            for (DataChannels channel : channels) {
                GL20.glEnableVertexAttribArray(channel.getLocation());
                GL20.glVertexAttribPointer(channel.getLocation(),channel.getSize(), GL11.GL_FLOAT, false, bytesPerVertex(channels), currentOffset);

                currentOffset += channel.getSize() * 4;
            }
        });
        attributesSetUp = true;
    }

    private static Map<EnumSet<DataChannels>, Integer> cache = new HashMap<>();

    public static int bytesPerVertex(EnumSet<DataChannels> channels) {
        if(cache.containsKey(channels)) {
            return cache.get(channels);
        } else {
            int sum = 0;
            for (DataChannels channel : channels) {
                sum += channel.getSize();
            }
            int bytesPerVertex = sum * 4;
            cache.put(channels, bytesPerVertex);
            return bytesPerVertex;
        }
    }

    public void delete() {
        GL30.glDeleteVertexArrays(id);
    }

    public int getBytesPerVertex() {
        return bytesPerVertex(channels);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
