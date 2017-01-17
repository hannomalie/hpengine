package de.hanno.hpengine.util;

public abstract class AbstractBuilder<T extends AbstractBuilder<T, RETURN_TYPE>, RETURN_TYPE> {
    protected abstract T me();
    public abstract RETURN_TYPE build();
}
