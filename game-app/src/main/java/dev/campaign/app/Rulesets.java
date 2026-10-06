package dev.campaign.app;

import dev.campaign.kernel.api.Ruleset;

import java.util.Map;

/** Wrapper bean: a raw Map bean would be mistaken by Spring for "all beans of this type". */
public record Rulesets(Map<String, Ruleset> byId) {}
