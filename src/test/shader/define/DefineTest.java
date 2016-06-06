package shader.define;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;

import static org.hamcrest.core.Is.is;

public class DefineTest {

    @Test(expected = IllegalArgumentException.class)
    public void testThrowsExceptionOnEmptyName() {
        Assert.assertThat("Correct define string should be generated!",
                "#define boolean myName true;\n",
                is(Define.getDefine("", true).getDefineString()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThrowsExceptionOnNullName() {
        Assert.assertThat("Correct define string should be generated!",
                "#define myName true;\n",
                is(Define.getDefine(null, true).getDefineString()));
    }

    @Test
    public void testBooleanDefineReturnsCorrectString() {
        Assert.assertThat("Correct define string should be generated!",
                "#define myName true\n",
                is(Define.getDefine("myName", true).getDefineString()));
    }

    @Test
    public void testIntDefineReturnsCorrectString() {
        Assert.assertThat("Correct define string should be generated!",
                "#define myName 4\n",
                is(Define.getDefine("myName", 4).getDefineString()));
    }

    @Test
    public void testFloatDefineReturnsCorrectString() {
        Assert.assertThat("Correct define string should be generated!",
                "#define myName 4.0\n",
                is(Define.getDefine("myName", 4.0).getDefineString()));
    }

    @Test
    public void testGetStringForDefinesReturnsCorrectString() {
        ArrayList<Define> defines = new ArrayList<>();
        defines.add(Define.getDefine("a", true));
        defines.add(Define.getDefine("b", 5));
        Assert.assertThat("Defines should be compiled into a correct string!",
                "#define a true\n#define b 5\n",
                is(Define.getStringForDefines(defines)));
    }

}
