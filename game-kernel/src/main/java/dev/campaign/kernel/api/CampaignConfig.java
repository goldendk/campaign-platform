package dev.campaign.kernel.api;

import dev.campaign.kernel.model.Participant;
import dev.campaign.kernel.util.Sorted;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything needed to start a campaign. phaseModes overrides the mode declared by the ruleset for a phase id,
 * which is how "simultaneous or take turns" is chosen by configuration.
 */
public record CampaignConfig(long seed, List<Participant> participants, Map<String, PhaseMode> phaseModes) {
    public CampaignConfig {
        participants = List.copyOf(participants);
        phaseModes = Sorted.map(phaseModes);
    }

    public static CampaignConfig of(long seed, Participant... participants) {
        return new CampaignConfig(seed, List.of(participants), Map.of());
    }

    public CampaignConfig withPhaseMode(String phaseId, PhaseMode mode) {
        Map<String, PhaseMode> copy = new TreeMap<>(phaseModes);
        copy.put(phaseId, mode);
        return new CampaignConfig(seed, new ArrayList<>(participants), copy);
    }
}
