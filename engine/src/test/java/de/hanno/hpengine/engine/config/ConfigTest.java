package de.hanno.hpengine.engine.config;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ConfigTest {

    @Test
    public void testFromPropertiesFile() {
        Config config = new Config();
        String propertiesString = "useParallax=true\nwidth=22";
        InputStream stream = new ByteArrayInputStream(propertiesString.getBytes(StandardCharsets.UTF_8));
        Config.Companion.populateConfigurationWithProperties(config, stream);
        Assert.assertTrue(config.isUseParallax());
        Assert.assertEquals(22, config.getWidth());
    }

}