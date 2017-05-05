package de.hanno.hpengine.renderer.material;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public class ListWithSyncedAdder<T> extends CopyOnWriteArrayList<T> {

    public synchronized T addIfAbsent(Supplier<T> supplier) {
        T instance = supplier.get();
        if(!contains(instance)) {
            add(instance);
            return instance;
        } else {
            return get(indexOf(instance));
        }
    }
}
