package test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import main.renderer.material.Material.MAP;
import main.shader.ShaderDefine;

import org.junit.Assert;
import org.junit.Test;

public class ShaderTest {

	@Test
	public void shaderDefines() {
		
		String expected = "#define use_diffuseMap\n#define use_normalMap\n";
		
		String defines = ShaderDefine.getDefineString(EnumSet.of(ShaderDefine.DIFFUSE, ShaderDefine.NORMAL));
		Assert.assertEquals(expected, defines);
		
		Set<MAP> mapSet = new HashSet<>();
		mapSet.add(MAP.DIFFUSE);
		mapSet.add(MAP.NORMAL);
		defines = ShaderDefine.getDefinesString(mapSet);

		Assert.assertTrue(expected.contains(ShaderDefine.DIFFUSE.getDefineText() + "\n"));
		Assert.assertTrue(expected.contains(ShaderDefine.NORMAL.getDefineText() + "\n"));
	}
}
