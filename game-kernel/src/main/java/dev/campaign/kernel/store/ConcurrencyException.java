package dev.campaign.kernel.store;

/** Another writer appended to the campaign first. Reload the campaign and retry. */
public class ConcurrencyException extends RuntimeException {
    public ConcurrencyException(String message) {
        super(message);
    }
}
