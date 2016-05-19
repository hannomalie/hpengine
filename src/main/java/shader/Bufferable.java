package shader;

public interface Bufferable {
    default int getElementsPerObject() { return get().length; }
    double[] get();
}
