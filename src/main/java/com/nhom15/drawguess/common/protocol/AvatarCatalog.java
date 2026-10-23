package com.nhom15.drawguess.common.protocol;

import java.util.List;

/** Shared allow-list for profile avatars sent through the protocol. */
public final class AvatarCatalog {
    public static final String DEFAULT = "starry-night";
    public static final List<String> KEYS = List.of(
            DEFAULT,
            "rhone-night",
            "cafe-night",
            "almond-blossoms",
            "irises",
            "saint-remy",
            "langlois-bridge",
            "wheatfield",
            "scheveningen-sea"
    );

    private AvatarCatalog() {
    }

    public static boolean isValid(String key) {
        return key != null && KEYS.contains(key);
    }

    public static String normalize(String key) {
        return isValid(key) ? key : DEFAULT;
    }
}
