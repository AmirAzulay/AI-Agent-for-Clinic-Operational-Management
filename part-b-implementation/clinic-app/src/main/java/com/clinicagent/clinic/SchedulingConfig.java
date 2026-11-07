package com.clinicagent.clinic;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} jobs: history retention now, the simulation tick and agent cycle later. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
