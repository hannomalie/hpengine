package de.hanno.hpengine.engine.component;

import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.EntityFactory;
import org.junit.Assert;
import org.junit.Test;
import de.hanno.hpengine.test.TestWithEngine;

import java.io.IOException;

public class JavaComponentTest extends TestWithEngine {

    @Test
    public void testCompilation() {
        JavaComponent component = new JavaComponent("public class Test implements Runnable {\n" +
                "\tstatic {\n" +
                "\t\tSystem.out.println(\"hello\");\n" +
                "\t}\n" +
                "\tpublic Test() {\n" +
                "\t\tSystem.out.println(\"world\");\n" +
                "\t}\n" +
                "\n" +
                "\t@Override\n" +
                "\tpublic void run() {\n" +
                "\t\tSystem.out.println(\"ran the yyy runnable\");\n" +
                "\t}\n" +
                "}");

        component.init();
        Assert.assertTrue(component.getCompiledClass().getSimpleName().equals("Test"));
        Assert.assertTrue(component.getInstance() instanceof Runnable);
    }

    @Test
    public void testJavaComponent() throws IOException, ClassNotFoundException {
        Entity entity = EntityFactory.getInstance().getEntity();
        entity.addComponent(new JavaComponent("public class Bla implements de.hanno.hpengine.engine.lifecycle.LifeCycle {" +
                "public void update(float seconds) { System.out.println(\"blubb\"); }" +
                "}"));
        engine.getScene().add(entity);
    }
}
