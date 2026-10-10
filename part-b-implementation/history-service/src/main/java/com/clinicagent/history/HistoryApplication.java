package com.clinicagent.history;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * history-service: owns Elasticsearch. Ingests clinic-state snapshots, AI decisions, system
 * events and evaluation results, and answers the history and trend queries used by the
 * dashboard, the agent's Context Builder and the evaluation harness.
 */
@SpringBootApplication
public class HistoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(HistoryApplication.class, args);
    }
}
