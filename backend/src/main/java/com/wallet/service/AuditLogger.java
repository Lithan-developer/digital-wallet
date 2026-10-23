package com.wallet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Writes to the dedicated AUDIT logger (see logback-spring.xml). Never log passwords or tokens here. */
@Component
public class AuditLogger {
    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    public void log(String action, Long userId, String outcome, String details) {
        AUDIT.info("action={} userId={} outcome={} {}", action, userId, outcome, details == null ? "" : details);
    }
}
