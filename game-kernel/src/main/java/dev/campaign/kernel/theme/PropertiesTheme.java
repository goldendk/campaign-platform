package dev.campaign.kernel.theme;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

/** A theme backed by a UTF-8 .properties file on the classpath. */
public final class PropertiesTheme implements Theme {
    private final Properties properties;

    private PropertiesTheme(Properties properties) {
        this.properties = properties;
    }

    public static PropertiesTheme fromClasspath(String resource) {
        try (InputStream in = PropertiesTheme.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalArgumentException("Theme resource not found: " + resource);
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return new PropertiesTheme(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static PropertiesTheme of(Properties properties) {
        return new PropertiesTheme(properties);
    }

    @Override
    public Optional<String> lookup(String key) {
        return Optional.ofNullable(properties.getProperty(key));
    }
}
