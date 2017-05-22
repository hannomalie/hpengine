package de.hanno.hpengine.engine.component;

public interface ScriptComponent {
    void reload();

    Object get(Object key);
    Object put(Object key, Object value);
}
