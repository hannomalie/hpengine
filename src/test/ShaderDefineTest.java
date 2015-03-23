package test;

import junit.framework.Assert;
import main.World;
import main.shader.ShaderDefine;

import org.junit.Test;

public class ShaderDefineTest {

	@Test
	public void generatesCorrectDefineString() {
		String actual = ShaderDefine.getGlobalDefinesString();
		Assert.assertTrue(actual.contains("const float RAINEFFECT = " + World.RAINEFFECT + ";\n"));
	}
}
