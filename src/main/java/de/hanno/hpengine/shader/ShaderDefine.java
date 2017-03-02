package de.hanno.hpengine.shader;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.renderer.material.Material.MAP;

import java.util.EnumSet;

public enum ShaderDefine {
	
	DIFFUSE(MAP.DIFFUSE),
	NORMAL(MAP.NORMAL),
	SPECULAR(MAP.SPECULAR),
	OCCLUSION(MAP.OCCLUSION),
	HEIGHT(MAP.HEIGHT),
	REFLECTION(MAP.REFLECTION),
	ROUGHNESS(MAP.ROUGHNESS);

	private String defineString;
	private MAP map;
	
	public String getDefineText() {
		return defineString;
	};
	
	private ShaderDefine(MAP map) {
		this.map = map;
		this.defineString = "#define use_" + map.shaderVariableName;
	}
	
	public static String getDefineString(EnumSet<ShaderDefine> defines) {
		if (defines == null) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		
		for (ShaderDefine shaderDefine : defines) {
			builder.append(shaderDefine.getDefineText());
			builder.append("\n");
		}
		return builder.toString();
	}

	public static String getGlobalDefinesString() {
		StringBuilder builder = new StringBuilder();

        appendWithSemicolonAndNewLine(builder, "const bool RAINEFFECT = " + (Engine.getInstance().getConfig().getRainEffect() != 0.0));
        appendWithSemicolonAndNewLine(builder, "const bool MULTIPLE_DIFFUSE_SAMPLES = " + Engine.getInstance().getConfig().isUseMultipleDiffuseSamples());
        appendWithSemicolonAndNewLine(builder, "const bool MULTIPLE_DIFFUSE_SAMPLES_PROBES = " + Engine.getInstance().getConfig().isUseMultipleDiffuseSamplesProbes());
        appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_DIFFUSE = " + Engine.getInstance().getConfig().isUseConetracingForDiffuse());
        appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_DIFFUSE_PROBES = " + Engine.getInstance().getConfig().isUseConetracingForDiffuseProbes());
        appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_SPECULAR = " + Engine.getInstance().getConfig().isUseConetracingForSpecular());
        appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_SPECULAR_PROBES = " + Engine.getInstance().getConfig().isUseConetracingForSpecularProbes());
        appendWithSemicolonAndNewLine(builder, "const bool PRECOMPUTED_RADIANCE = " + Engine.getInstance().getConfig().isUsePrecomputedRadiance());
        appendWithSemicolonAndNewLine(builder, "const bool SCATTERING = " + Engine.getInstance().getConfig().isScattering());
        appendWithSemicolonAndNewLine(builder, "const bool CALCULATE_ACTUAL_RADIANCE = " + Engine.getInstance().getConfig().isCalculateActualRadiance());
        appendWithSemicolonAndNewLine(builder, "const bool SSR_FADE_TO_SCREEN_BORDERS = " + Engine.getInstance().getConfig().isSsrFadeToScreenBorders());
        appendWithSemicolonAndNewLine(builder, "const bool SSR_TEMPORAL_FILTERING = " + Engine.getInstance().getConfig().isSsrTemporalFiltering());
        appendWithSemicolonAndNewLine(builder, "const bool USE_BLOOM = " + Engine.getInstance().getConfig().isUseBloom());
        appendWithSemicolonAndNewLine(builder, "const bool USE_PCF = " + Engine.getInstance().getConfig().isUsePcf());
        appendWithSemicolonAndNewLine(builder, "const bool USE_DPSM = " + Engine.getInstance().getConfig().isUseDpsm());
		
		return builder.toString();
	}
	
	private static StringBuilder appendWithSemicolonAndNewLine(StringBuilder builder, String toAppend) {
		builder.append(toAppend);
		builder.append(";\n");
		return builder;
	}
}
