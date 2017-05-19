package de.hanno.hpengine.shader;

import java.nio.ByteBuffer;

public interface Bufferable {
    void putToBuffer(ByteBuffer buffer);

    int getBytesPerObject();
}
