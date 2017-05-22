package de.hanno.hpengine.engine.graphics.state;

import java.util.List;

public interface RenderStateRecorder {
    boolean add(RenderState state);

    List<RenderState> getStates();
}
