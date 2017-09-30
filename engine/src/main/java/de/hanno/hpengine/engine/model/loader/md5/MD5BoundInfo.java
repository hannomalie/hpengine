package de.hanno.hpengine.engine.model.loader.md5;

import de.hanno.hpengine.engine.model.StaticMesh;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MD5BoundInfo {

    private List<MD5Bound> bounds;

    public List<MD5Bound> getBounds() {
        return bounds;
    }

    public void setBounds(List<MD5Bound> bounds) {
        this.bounds = bounds;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("bounds [" + System.lineSeparator());
        for (MD5Bound bound : bounds) {
            str.append(bound).append(System.lineSeparator());
        }
        str.append("]").append(System.lineSeparator());
        return str.toString();
    }

    public static MD5BoundInfo parse(List<String> blockBody) {
        MD5BoundInfo result = new MD5BoundInfo();
        List<MD5Bound> bounds = new ArrayList<>();
        for (String line : blockBody) {
            MD5Bound bound = MD5Bound.parseLine(line);
            if (bound != null) {
                bounds.add(bound);
            }
        }
        result.setBounds(bounds);
        return result;
    }

    public static class MD5Bound {

        private static final Pattern PATTERN_BOUND = Pattern.compile("\\s*" + MD5Utils.VECTOR3_REGEXP + "\\s*" + MD5Utils.VECTOR3_REGEXP + ".*");

        private Vector3f minBound;

        private Vector3f maxBound;
        private Vector3f[] minMax = new Vector3f[2];
        Vector3f center = new Vector3f();
        private float boundingSphereRadius;

        public Vector3f getMinBound() {
            return minBound;
        }

        public void setMinBound(Vector3f minBound) {
            this.minBound = minBound;
        }

        public Vector3f getMaxBound() {
            return maxBound;
        }

        public void setMaxBound(Vector3f maxBound) {
            this.maxBound = maxBound;
            this.minMax[1] = maxBound;
            this.minMax[0] = minBound;
            calculateCenter();
            this.boundingSphereRadius = StaticMesh.getBoundingSphereRadius(minMax[0], minMax[1]);
        }

        Vector3f centerTemp = new Vector3f();
        private void calculateCenter() {
            center = centerTemp.set(minMax[0]).add(new Vector3f(minMax[1]).sub(minMax[0]).mul(0.5f));
        }
        @Override
        public String toString() {
            return "[minBound: " + minBound + ", maxBound: " + maxBound + "]";
        }

        public static MD5Bound parseLine(String line) {
            MD5Bound result = null;
            Matcher matcher = PATTERN_BOUND.matcher(line);
            if (matcher.matches()) {
                result = new MD5Bound();
                float x = Float.parseFloat(matcher.group(1));
                float y = Float.parseFloat(matcher.group(2));
                float z = Float.parseFloat(matcher.group(3));
                result.setMinBound(new Vector3f(x, y, z));

                x = Float.parseFloat(matcher.group(4));
                y = Float.parseFloat(matcher.group(5));
                z = Float.parseFloat(matcher.group(6));
                result.setMaxBound(new Vector3f(x, y, z));
            }
            return result;
        }

        public Vector3f[] getMinMax() {
            return minMax;
        }

        public Vector3f getCenter() {
            return center;
        }

        public float getBoundingSphereRadius() {
            return boundingSphereRadius;
        }
    }
}
