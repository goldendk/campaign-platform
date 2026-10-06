package dev.campaign.kernel.id;

/** Typed string identifier. Serialised as a plain JSON string by game-json. */
public interface Id {
    String value();
}
