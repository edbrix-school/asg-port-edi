package com.asg.portediintegration.scheduler;

import com.asg.portediintegration.service.EdiOrchestrationService;
import com.asg.portediintegration.service.GlobalParameterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class DynamicEdiScheduler implements SchedulingConfigurer {

    private final EdiOrchestrationService ediOrchestrationService;
    private final GlobalParameterService globalParameterService;
    private static final String SCHEDULER_ENABLED_KEY = "PORT_EDI_SCHEDULER_ENABLED";

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(this::runEdiPipeline, triggerContext -> {

                    boolean enabled = globalParameterService.getBoolean(SCHEDULER_ENABLED_KEY, true);
                    log.info("Scheduler enabled: {}", enabled);

                    if (!enabled) {
                        log.debug("EDI scheduler is disabled, rechecking in 1 minute");
                        return Instant.now().plus(1, ChronoUnit.MINUTES);
                    }

                    String cron = globalParameterService.getValue("PORT_EDI_SCHEDULER_CRON");

                    if (StringUtils.isBlank(cron)) {
                        cron = "0 */15 * * * ?";
                    }

                    log.debug("Using cron expression: {}", cron);

                    try {
                        CronTrigger cronTrigger = new CronTrigger(cron);
                        return cronTrigger.nextExecution(triggerContext);
                    } catch (Exception e) {
                        log.error("Invalid cron expression: {}", cron, e);
                        return Instant.now().plus(15, ChronoUnit.MINUTES);
                    }
                }
        );
    }

    private void runEdiPipeline() {
        // Guard at execution-time as well, because the "enabled" flag can be
        // toggled after the trigger has already scheduled the next run.
        boolean enabled = globalParameterService.getBoolean(SCHEDULER_ENABLED_KEY, true);
        if (!enabled) {
            log.info("Scheduled EDI pipeline skipped ({}=false)", SCHEDULER_ENABLED_KEY);
            return;
        }

        log.info("Starting scheduled EDI pipeline");
        try {
            ediOrchestrationService.execute();
            log.info("EDI pipeline completed successfully");
        } catch (Exception e) {
            log.error("Error during EDI pipeline execution", e);
        }
    }
}