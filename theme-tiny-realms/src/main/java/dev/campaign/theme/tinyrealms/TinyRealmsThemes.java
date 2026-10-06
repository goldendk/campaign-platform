package dev.campaign.theme.tinyrealms;

import dev.campaign.kernel.theme.PropertiesTheme;
import dev.campaign.kernel.theme.Theme;

/** The two vocabularies shipped for the tiny-realms ruleset. Add a language by adding a properties file. */
public final class TinyRealmsThemes {
    private TinyRealmsThemes() {}

    public static Theme fantasy() {
        return PropertiesTheme.fromClasspath("themes/tiny-realms/fantasy.properties");
    }

    public static Theme spacefaring() {
        return PropertiesTheme.fromClasspath("themes/tiny-realms/spacefaring.properties");
    }
}
