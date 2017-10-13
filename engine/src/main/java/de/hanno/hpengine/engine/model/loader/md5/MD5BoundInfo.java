package de.hanno.hpengine.engine.model.loader.md5;

import de.hanno.hpengine.engine.transform.SimpleSpatial;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Float.parseFloat;

public class MD5BoundInfo {

    private static final Logger LOGGER = Logger.getLogger(MD5BoundInfo.class.getName());

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

    public static class MD5Bound extends SimpleSpatial {

        private static final Pattern PATTERN_BOUND = Pattern.compile("\\s*" + MD5Utils.VECTOR3_REGEXP + "\\s*" + MD5Utils.VECTOR3_REGEXP + ".*");

        private Vector3f[] minMax = new Vector3f[2];

        public MD5Bound(Vector3f min, Vector3f max) {
            this.minMax[0] = min;
            this.minMax[1] = max;
        }

        @Override
        public String toString() {
            return "[minBound: " + minMax[0] + ", maxBound: " + minMax[1] + "]";
        }

        public static MD5Bound parseLine(String line) {
            Matcher matcher = PATTERN_BOUND.matcher(line);
            if (matcher.matches()) {
                float xMin = parseFloat(matcher.group(1));
                float yMin = parseFloat(matcher.group(2));
                float zMin = parseFloat(matcher.group(3));

                float xMax = parseFloat(matcher.group(4));
                float yMax = parseFloat(matcher.group(5));
                float zMax = parseFloat(matcher.group(6));

                MD5Bound result = new MD5Bound(new Vector3f(xMin, yMin, zMin), new Vector3f(xMax, yMax, zMax));
                return result;
            }
            LOGGER.warning("Cannot parse bound: " + line);
            return null;
        }

        @Override
        public Vector3f[] getMinMax() {
            return minMax;
        }

    }
}
