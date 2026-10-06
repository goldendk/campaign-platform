package dev.campaign.kernel.model;

import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.util.Sorted;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Something that has to be settled outside the app before the campaign may move on, typically a tabletop battle.
 * Parties report an outcome; agreeing reports resolve the item, disagreeing reports send it to the GM.
 */
public record PendingItem(
        String id,
        String kind,
        Set<FactionId> parties,
        List<String> options,
        Map<String, String> data,
        Map<FactionId, String> reports,
        String adjudication,
        boolean disputed,
        boolean resolved,
        String outcome) {

    public PendingItem {
        parties = Sorted.set(parties);
        options = List.copyOf(options);
        data = Sorted.map(data);
        reports = Sorted.map(reports);
    }

    public static PendingItem open(String id, String kind, Set<FactionId> parties, List<String> options, Map<String, String> data) {
        return new PendingItem(id, kind, parties, options, data, Map.of(), null, false, false, null);
    }

    public PendingItem withReport(FactionId faction, String reported) {
        Map<FactionId, String> copy = new TreeMap<>(reports);
        copy.put(faction, reported);
        return new PendingItem(id, kind, parties, options, data, copy, adjudication, disputed, resolved, outcome);
    }

    public PendingItem withAdjudication(String ruling) {
        return new PendingItem(id, kind, parties, options, data, reports, ruling, disputed, resolved, outcome);
    }

    public PendingItem asDisputed() {
        return new PendingItem(id, kind, parties, options, data, reports, adjudication, true, resolved, outcome);
    }

    public PendingItem asResolved(String finalOutcome) {
        return new PendingItem(id, kind, parties, options, data, reports, adjudication, disputed, true, finalOutcome);
    }
}
