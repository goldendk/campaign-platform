package dev.campaign.app;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The timer. All the logic about what a missed deadline means lives in the kernel's tick(). */
@Component
class DeadlineScheduler {
    private final CampaignRuntime runtime;

    DeadlineScheduler(CampaignRuntime runtime) {
        this.runtime = runtime;
    }

    @Scheduled(fixedDelayString = "${campaign.tick-interval-ms:60000}")
    void tick() {
        runtime.tickAll();
    }
}
