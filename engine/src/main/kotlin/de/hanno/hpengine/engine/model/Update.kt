package de.hanno.hpengine.engine.model

enum class Update(val value: Int) {
    STATIC(1), DYNAMIC(0);

    val asDouble: Double get() = value.toDouble()
}