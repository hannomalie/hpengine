package de.hanno.hpengine.engine;

import java.awt.*;

public class CanvasWrapper {
    private final Canvas canvas;
    private final Runnable setTitleRunnable;

    public CanvasWrapper(Canvas canvas, Runnable setTitleRunnable) {
        if(canvas == null || setTitleRunnable == null) {
            throw new IllegalArgumentException("Canvas or runnable is null");
        }
        this.canvas = canvas;
        this.setTitleRunnable = setTitleRunnable;
    }

    public Canvas getCanvas() {
        return canvas;

    }

    public Runnable getSetTitleRunnable() {
        return setTitleRunnable;
    }
}
