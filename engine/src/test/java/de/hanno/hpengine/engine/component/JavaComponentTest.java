package de.hanno.hpengine.engine.component;

import de.hanno.hpengine.engine.entity.Entity;
import org.junit.Assert;
import org.junit.Test;
import de.hanno.hpengine.TestWithEngine;

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

        component.init(engine);
        Assert.assertTrue(component.getCompiledClass().getSimpleName().equals("Test"));
        Assert.assertTrue(component.getInstance() instanceof Runnable);
    }

    @Test
    public void testJavaComponent() throws IOException, ClassNotFoundException {
        Entity entity = engine.getSceneManager().getScene().getEntityManager().create();
        entity.addComponent(new JavaComponent("public class Bla implements de.hanno.hpengine.managerContext.lifecycle.LifeCycle {" +
                "public void update(float seconds) { System.out.println(\"blubb\"); }" +
                "}"));
        engine.getSceneManager().getScene().add(entity);
    }
}
