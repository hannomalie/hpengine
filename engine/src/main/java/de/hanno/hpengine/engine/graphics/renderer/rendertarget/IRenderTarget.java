package de.hanno.hpengine.engine.graphics.renderer.rendertarget;

public interface IRenderTarget {
    void use(boolean clear);

    int getWidth();

    void setWidth(int width);

    int getHeight();

    void setHeight(int height);

    int getFrameBuffer();

    String getName();

    void setName(String name);
}
