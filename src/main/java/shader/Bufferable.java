package shader;

public interface Bufferable {
    default int getSizePerObject() { return get().length; }
    float[] get();
}
