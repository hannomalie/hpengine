package shader;

public interface Bufferable {
    default int getSizePerObject() { return get().length; }
    double[] get();
}
