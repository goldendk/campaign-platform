package dev.campaign.kernel.api;

/**
 * Marker for a player (or GM) intention. A command is plain immutable data: it is the request body, the thing
 * stored while orders are staged, and the input to the handler. There is no separate DTO.
 *
 * Declare commands as records. Component types drive the generated UI prompts (see CommandSchema):
 * EntityId = pick an entity, LocationId = pick a location, List&lt;LocationId&gt; = pick a path, etc.
 */
public interface Command {}
