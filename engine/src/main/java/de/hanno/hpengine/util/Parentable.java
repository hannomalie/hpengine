package de.hanno.hpengine.util;

import java.util.List;

public interface Parentable <T extends Parentable> {
    List<T> getChildren();
    default void addChildren(List<T> children) { getChildren().addAll(children); }
    default T addChildRelationship(T child) {
        child.setParent(this);
        addChild(child);
        return child;
    }
    default T addChild(T child) {
        if(!getChildren().contains(child)) {
            getChildren().add(child);
        }
        return child;
    }

    void setParent(T parent);
    default void removeParent() {
        getParent().getChildren().remove(this);
        setParent(null);
    }
    T getParent();
    default boolean hasParent() {
        return getParent() != null;
    }

    default boolean hasChildren() {
        return !getChildren().isEmpty();
    }
}
