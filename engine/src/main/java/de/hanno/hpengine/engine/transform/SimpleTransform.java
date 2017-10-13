package de.hanno.hpengine.engine.transform;

import java.util.ArrayList;
import java.util.List;

public class SimpleTransform extends Transform<SimpleTransform> {
    private List<SimpleTransform> children = new ArrayList<>();

    public SimpleTransform(Transform downFacing) {
        set(downFacing);
    }

    public SimpleTransform() {
    }

    @Override
    public List<SimpleTransform> getChildren() {
        return children;
    }
}
