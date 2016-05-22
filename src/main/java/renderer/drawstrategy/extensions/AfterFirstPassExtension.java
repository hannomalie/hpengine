package renderer.drawstrategy.extensions;

public interface AfterFirstPassExtension extends RenderExtension {

    interface AfterFirstPassExtensionPoint extends ExtensionPoint {
        void registerAfterFirstPassExtension(AfterFirstPassExtension extension);
    }
}
