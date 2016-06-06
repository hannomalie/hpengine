package shader.define;

import com.google.common.base.Strings;

import java.util.List;

public abstract class Define<T> {

    protected final String name;
    protected final T backingValue;

    public String getName() {
        return name;
    }

    private Define(String name, T backingValue) {
        if(Strings.isNullOrEmpty(name)) { throw new IllegalArgumentException("No empty name for define!"); }
        if(backingValue == null) { throw new IllegalArgumentException("No null value for define!"); }
        this.name = name;
        this.backingValue = backingValue;
    }

    public T getBackingValue() {
        return backingValue;
    }

    public abstract String getDefineString();

    public static <TYPE> Define<TYPE> getDefine(String name, TYPE backingValue) {
        return new Define<TYPE>(name, backingValue) {
            @Override
            public String getDefineString() {
                return "#define " + name + " " + String.valueOf(backingValue) + "\n";
            }
        };
    }

    public static String getStringForDefines(List<Define> defines) {
        StringBuilder builder = new StringBuilder("");
        for(Define define : defines) {
            builder.append(define.getDefineString());
        }

        return builder.toString();
    }

}
