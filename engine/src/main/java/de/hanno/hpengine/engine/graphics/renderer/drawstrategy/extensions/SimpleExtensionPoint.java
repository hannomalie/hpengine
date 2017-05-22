package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions;

import java.util.ArrayList;
import java.util.List;

public class SimpleExtensionPoint<EXTENSION extends Runnable> implements ExtensionPoint {
    private final List<EXTENSION> extensions = new ArrayList();

    public void register(EXTENSION extension) {
        extensions.add(extension);
    }

    public void run() {
        for (EXTENSION extension : extensions) {
            extension.run();
        }
    }
}
