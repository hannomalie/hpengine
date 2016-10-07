package component;

import de.hanno.compiler.InMemoryJavaCompiler;
import engine.lifecycle.LifeCycle;
import util.script.ScriptManager;

public class JavaComponent extends BaseComponent implements ScriptComponent {

    static final InMemoryJavaCompiler compiler = new InMemoryJavaCompiler();
    String sourceCode = "";

    private Class<?> compiledClass;
    private boolean isLifeCycle;
    private Object instance;

    public JavaComponent(String sourceCode) {
        super();
        this.sourceCode = sourceCode;
    }

    public JavaComponent() {
        super();
    }

    @Override
    public String getIdentifier() {
        return "JavaComponent";
    }

    @Override
    public void init() {
        if(!isInitialized()) {
            initWrappingComponent();
        }
        if(isLifeCycle) {
            ((LifeCycle) instance).init();
        }
    }

    @Override
    public void update(float seconds) {
        if(isLifeCycle) {
            ((LifeCycle) instance).update(seconds);
        }
    }

    public String getSourceCode() {
        return sourceCode;
    }

    @Override
    public void reload() {
        initWrappingComponent();
    }

    private void initWrappingComponent() {
        super.init();
        try {
            compiledClass = compiler.compile(sourceCode);
            instance = compiledClass.getConstructors()[0].newInstance();
            isLifeCycle = instance instanceof LifeCycle;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Class<?> getCompiledClass() {
        return compiledClass;
    }

    public Object getInstance() {
        return instance;
    }
}
