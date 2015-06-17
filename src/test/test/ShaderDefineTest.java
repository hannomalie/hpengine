package test;

import engine.World;
import junit.framework.Assert;
import org.junit.Test;
import shader.ShaderDefine;

public class ShaderDefineTest {

	@Test
	public void generatesCorrectDefineString() {
		String actual = ShaderDefine.getGlobalDefinesString();
		Assert.assertTrue(actual.contains("const bool RAINEFFECT = " + (World.RAINEFFECT != 0.0) + ";\n"));
	}
}
