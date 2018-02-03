package de.hanno.hpengine.engine.model;

import de.hanno.hpengine.engine.Engine;
import org.lwjgl.opengl.*;

import java.util.*;

public class VertexArrayObject {

    private final EnumSet<DataChannels> channels;
    private int id = -1;
    private transient boolean attributesSetUp = false;


    public static VertexArrayObject getForChannels(EnumSet<DataChannels> channels) {
        return new VertexArrayObject(channels);
    }

    private VertexArrayObject(EnumSet<DataChannels> channels) {
        this.channels = channels.clone();
        Engine.getInstance().getGpuContext().execute(() -> setId(GL30.glGenVertexArrays()));
        setUpAttributes();
    }

    private void unbind() {
        Engine.getInstance().getGpuContext().execute(() -> GL30.glBindVertexArray(0));
    }

    public void bind() {
//        if(CURRENTLY_BOUND_VAO == id) { return; }
        Engine.getInstance().getGpuContext().execute(bindRunnable);
    }

    private Runnable bindRunnable = () -> {
        if (getId() <= 0) {
            setId(GL30.glGenVertexArrays());
        }
        GL30.glBindVertexArray(getId());
    };

    private void setUpAttributes() {
        if(attributesSetUp) { return; }
        Engine.getInstance().getGpuContext().execute(() -> {
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
