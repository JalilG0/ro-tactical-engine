package com.mil.trdss.ro.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Ro Tactical Decision Support & Triage Engine API",
                version = "1.0.0",
                description = "Stateless, reactive REST Gateway for the Ro Tactical Decision Support & Triage Engine. "
                        + "Accepts real-time drone telemetry heartbeats and target intake events from Ground Control "
                        + "Station (GCS) operators, and returns Top-10 hierarchical tactical recommendation payloads "
                        + "computed by the Overmatch, Swarm Allocation, Weather/EW, and Deterministic Tie-Breaking "
                        + "engines. This system is NOT a Command & Control (C2) or flight control system; it produces "
                        + "recommendations only, and all final engagement decisions remain with the human operator."
        )
)
public class OpenApiConfig {
}
