package dev.campaign.kernel.api;

/**
 * Marker for a fact that happened. Events are the only thing that ever changes game state, and the only thing
 * that is persisted. Declare them as records. Rulesets may add their own (register with a reducer).
 */
public interface Event {}
