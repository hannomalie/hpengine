package de.hanno.hpengine.graphics.constants

enum class BlendMode {
    FUNC_ADD;

    enum class Factor {
        ZERO,
        ONE,
        SRC_ALPHA,
        ONE_MINUS_SRC_ALPHA,
    }
}