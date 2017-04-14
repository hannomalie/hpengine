package de.hanno.hpengine.renderer.material;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector3f;
import de.hanno.hpengine.texture.Texture;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.nio.IntBuffer;

import static de.hanno.hpengine.renderer.material.Material.MaterialType.DEFAULT;

/**
 * Created by pernpeintner on 19.09.2016.
 */
public final class MaterialInfo implements Serializable {
    private static final long serialVersionUID = 3564429930446909410L;

    public MaterialInfo(MaterialMap maps) {
        this.maps = maps;
    }

    public MaterialInfo setName(String name) {
        this.name = name;
        return this;
    }

    public MaterialInfo() {
    }

    public MaterialInfo(String name, MaterialMap maps) {
        this(maps);
    }

    public MaterialInfo(MaterialInfo materialInfo) {
        this.maps = materialInfo.maps;
        this.environmentMapType = materialInfo.environmentMapType;
        this.name = materialInfo.name;
        this.diffuse = new Vector3f(materialInfo.diffuse);
        this.roughness = materialInfo.roughness;
        this.metallic = materialInfo.metallic;
        this.ambient = materialInfo.ambient;
        this.transparency = materialInfo.transparency;
        this.parallaxScale = materialInfo.parallaxScale;
        this.parallaxBias = materialInfo.parallaxBias;
        this.materialType = materialInfo.materialType;
    }

    public MaterialMap maps = new MaterialMap();
    public Material.ENVIRONMENTMAPTYPE environmentMapType = Material.ENVIRONMENTMAPTYPE.GENERATED;
    public String name = "";
    public Vector3f diffuse = new Vector3f(1, 1, 1);
    public float roughness = 0.95f;
    public float metallic = 0f;
    public float ambient = 0;
    public float transparency = 0;
    public float parallaxScale = 0.04f;
    public float parallaxBias = 0.02f;
    public Material.MaterialType materialType = DEFAULT;

    public boolean textureLess;

    public void put(Material.MAP map, Texture texture) {
        maps.put(map, texture);
        textureIdsCache.clear();
    }

    public MaterialInfo setRoughness(float roughness) {
        this.roughness = roughness;
        return this;
    }

    public MaterialInfo setMetallic(float metallic) {
        this.metallic = metallic;
        return this;
    }

    public MaterialInfo setDiffuse(Vector3f diffuse) {
        this.diffuse.set(diffuse);
        return this;
    }
    public MaterialInfo setMaterialType(Material.MaterialType type) {
        this.materialType = type;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MaterialInfo that = (MaterialInfo) o;

        if (Float.compare(that.roughness, roughness) != 0) return false;
        if (Float.compare(that.metallic, metallic) != 0) return false;
        if (Float.compare(that.ambient, ambient) != 0) return false;
        if (Float.compare(that.transparency, transparency) != 0) return false;
        if (Float.compare(that.parallaxScale, parallaxScale) != 0) return false;
        if (Float.compare(that.parallaxBias, parallaxBias) != 0) return false;
        if (textureLess != that.textureLess) return false;
        if (maps != null ? !maps.equals(that.maps) : that.maps != null) return false;
        if (environmentMapType != that.environmentMapType) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (diffuse != null ? !diffuse.equals(that.diffuse) : that.diffuse != null) return false;
        return materialType == that.materialType;

    }

    @Override
    public int hashCode() {
        int result = maps != null ? maps.hashCode() : 0;
        result = 31 * result + (environmentMapType != null ? environmentMapType.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (diffuse != null ? diffuse.hashCode() : 0);
        result = 31 * result + (roughness != +0.0f ? Float.floatToIntBits(roughness) : 0);
        result = 31 * result + (metallic != +0.0f ? Float.floatToIntBits(metallic) : 0);
        result = 31 * result + (ambient != +0.0f ? Float.floatToIntBits(ambient) : 0);
        result = 31 * result + (transparency != +0.0f ? Float.floatToIntBits(transparency) : 0);
        result = 31 * result + (parallaxScale != +0.0f ? Float.floatToIntBits(parallaxScale) : 0);
        result = 31 * result + (parallaxBias != +0.0f ? Float.floatToIntBits(parallaxBias) : 0);
        result = 31 * result + (materialType != null ? materialType.hashCode() : 0);
        result = 31 * result + (textureLess ? 1 : 0);
        return result;
    }

    public MaterialInfo setAmbient(float ambient) {
        this.ambient = ambient;
        return this;
    }


    private transient WeakReference<IntBuffer> textureIdsCache = null;

    private void cacheTextures() {
        if (textureIdsCache == null || textureIdsCache.get() == null) {
            textureIdsCache = new WeakReference<>(BufferUtils.createIntBuffer(Material.MAP.values().length));
            textureIdsCache.get().rewind();
            int[] ints = new int[Material.MAP.values().length];
            for(int i = 0; i < Material.MAP.values().length - 1; i++) {
                Texture texture = maps.get(Material.MAP.values()[i]);
                ints[i] = texture != null ? texture.getTextureID() : 0;
            }
            textureIdsCache.get().put(ints);
            textureIdsCache.get().rewind();
        }
    }

    public IntBuffer getTextureIds() {
        cacheTextures();
        return textureIdsCache.get();
    }
}
