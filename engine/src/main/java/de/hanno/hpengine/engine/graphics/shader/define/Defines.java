package de.hanno.hpengine.engine.graphics.shader.define;

import java.util.ArrayList;
import java.util.Arrays;

public class Defines extends ArrayList<Define> {
    public Defines(Define<Boolean> ... defines) {
        addAll(Arrays.asList(defines));
    }

    @Override
    public String toString() {
        if(isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for(int i = 0; i < size(); i++) {
            result.append(get(i).getDefineString());
        }
        return result.toString();
    }
}
