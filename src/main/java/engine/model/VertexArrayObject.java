package engine.model;

import org.lwjgl.opengl.*;
import renderer.OpenGLContext;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VertexArrayObject {

    private final EnumSet<DataChannels> channels;
    private int id = -1;
    private transient boolean attributesSetUp = false;


    public static VertexArrayObject getForChannels(EnumSet<DataChannels> channels) {
        return new VertexArrayObject(channels);
    }

    private VertexArrayObject(EnumSet<DataChannels> channels) {
        this.channels = channels.clone();
        OpenGLContext.getInstance().execute(() -> setId(GL30.glGenVertexArrays()));
        setUpAttributes();
    }

    private void unbind() {
        OpenGLContext.getInstance().execute(() -> GL30.glBindVertexArray(0));
    }

    public void bind() {
//        if(CURRENTLY_BOUND_VAO == id) { return; }
        OpenGLContext.getInstance().execute(() -> {
            GL30.glBindVertexArray(getId());
        });
    }

    private void setUpAttributes() {
        if(attributesSetUp) { return; }
        OpenGLContext.getInstance().execute(() -> {
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
