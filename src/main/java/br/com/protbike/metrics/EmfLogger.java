package br.com.protbike.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class EmfLogger {

    private static final Logger LOG = Logger.getLogger(EmfLogger.class);
    private final ObjectMapper mapper = new ObjectMapper();

    public void logMetricas(Map<String, Number> metricas) {
        try {
            Map<String, Object> payload = Map.of(
                    "_aws", Map.of(
                            "Timestamp", System.currentTimeMillis(),
                            "CloudWatchMetrics", List.of(
                                    Map.of(
                                            "Namespace", "Protbike/Notificacoes",
                                            "Dimensions", List.of(List.of("service")),
                                            "Metrics", metricas.keySet().stream()
                                                    .map(name -> Map.of("Name", name, "Unit", "Count"))
                                                    .toList()
                                    )
                            )
                    ),
                    "service", "lambda-notificacao"
            );

            Map<String, Object> finalPayload = new java.util.HashMap<>(payload);
            finalPayload.putAll(metricas);

            LOG.info(mapper.writeValueAsString(finalPayload));

        } catch (Exception e) {
            LOG.error("Falha ao emitir métricas EMF", e);
        }
    }
}
