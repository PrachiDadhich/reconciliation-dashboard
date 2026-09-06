package com.ledgermatch.explanations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgermatch.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
public class ExplanationController {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final RestClient client;
    private final String apiKey;
    private static final String MODEL = "gpt-4o-mini";
    private static final double TEMPERATURE = 0.2;

    public ExplanationController(JdbcTemplate db, ObjectMapper json, @Value("${app.openai-api-key:}") String apiKey) {
        this.db = db;
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.client = RestClient.builder().baseUrl("https://api.openai.com/v1").build();
    }

    @PostMapping("/discrepancies/{id}/explain")
    public Map<String, Object> explain(@PathVariable UUID id) {
        UUID user = CurrentUser.id();
        List<Map<String, Object>> cached = db.query("select model,temperature,likely_cause,recommended_action,confidence from discrepancy_explanations e join discrepancies d on d.id=e.discrepancy_id where e.discrepancy_id=? and d.user_id=?", (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("available", true);
            row.put("source", "cached-llm");
            row.put("model", rs.getString("model"));
            row.put("temperature", rs.getBigDecimal("temperature"));
            row.put("likelyCause", rs.getString("likely_cause"));
            row.put("recommendedAction", rs.getString("recommended_action"));
            row.put("confidence", rs.getString("confidence"));
            return row;
        }, id, user);
        if (!cached.isEmpty()) return cached.getFirst();

        var rows = db.query("select d.type,d.severity,d.amount_at_risk,d.details,o.order_id,o.customer_email from discrepancies d left join orders o on o.id=d.order_id and o.user_id=d.user_id where d.id=? and d.user_id=?", (rs, n) -> {
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("type", rs.getString("type"));
            fact.put("severity", rs.getString("severity"));
            fact.put("amount", rs.getBigDecimal("amount_at_risk"));
            fact.put("details", rs.getString("details"));
            fact.put("orderId", rs.getString("order_id"));
            fact.put("customerEmail", rs.getString("customer_email"));
            return fact;
        }, id, user);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        Map<String, Object> fact = rows.getFirst();
        if (apiKey.isBlank()) return fallback(fact, "No OpenAI API key configured; showing deterministic guidance.");

        JsonNode answer = requestExplanation(fact);
        if (answer == null) return fallback(fact, "The LLM was unavailable or returned an invalid response; showing deterministic guidance.");

        try {
            String cause = answer.get("likely_cause").asText().trim();
            String action = answer.get("recommended_action").asText().trim();
            String confidence = answer.get("confidence").asText().trim();
            Map<String, Object> out = Map.of("available", true, "source", "llm", "model", MODEL, "temperature", TEMPERATURE, "likelyCause", cause, "recommendedAction", action, "confidence", confidence);
            db.update("insert into discrepancy_explanations(discrepancy_id,model,temperature,likely_cause,recommended_action,confidence,raw_response) values (?,?,?,?,?,?,?::jsonb)", id, MODEL, TEMPERATURE, cause, action, confidence, answer.toString());
            return out;
        } catch (Exception ignored) {
            return fallback(fact, "The LLM response could not be saved; showing deterministic guidance.");
        }
    }

    private JsonNode requestExplanation(Map<String, Object> fact) {
        try {
            String facts = json.writeValueAsString(fact);
            String prompt = "Explain this deterministic reconciliation discrepancy for a revenue operations user. Use only the supplied facts. Return JSON with exactly three string fields: likely_cause, recommended_action, confidence. Mention the affected order and amount when available. Do not invent transactions, dates, or policy. Facts: " + facts;
            String body = json.writeValueAsString(Map.of("model", MODEL, "temperature", TEMPERATURE, "messages", List.of(Map.of("role", "system", "content", "You explain accounting exceptions clearly and concisely."), Map.of("role", "user", "content", prompt)), "response_format", Map.of("type", "json_object")));
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    String raw = client.post().uri("/chat/completions").header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json").body(body).retrieve().body(String.class);
                    JsonNode root = json.readTree(raw);
                    String content = root.path("choices").path(0).path("message").path("content").asText("");
                    JsonNode parsed = json.readTree(content);
                    if (validAnswer(parsed)) return parsed;
                } catch (Exception ignored) {
                    // Retry transient provider and malformed-response failures once.
                }
            }
        } catch (Exception ignored) {
            // The deterministic fallback below keeps the dashboard useful.
        }
        return null;
    }

    private boolean validAnswer(JsonNode answer) {
        return answer != null && answer.isObject() && answer.path("likely_cause").isTextual() && !answer.path("likely_cause").asText().isBlank() && answer.path("recommended_action").isTextual() && !answer.path("recommended_action").asText().isBlank() && answer.path("confidence").isTextual() && !answer.path("confidence").asText().isBlank();
    }

    private Map<String, Object> fallback(Map<String, Object> fact, String reason) {
        String type = String.valueOf(fact.get("type"));
        String order = String.valueOf(fact.getOrDefault("orderId", "the affected record"));
        String amount = String.valueOf(fact.getOrDefault("amount", "an unknown amount"));
        String cause;
        String action;
        switch (type) {
            case "MISSING_PAYMENT" -> { cause = order + " has no matching payment, putting " + amount + " of expected revenue at risk."; action = "Check the processor for a failed or omitted charge, then confirm whether the customer should be contacted or the order cancelled."; }
            case "ORPHAN_PAYMENT" -> { cause = "Payment activity for " + order + " has no matching order in the uploaded store export."; action = "Search the store system for this order and verify whether the order export was incomplete or the payment was attached to the wrong order."; }
            case "AMOUNT_MISMATCH" -> { cause = order + " has a settled amount that differs from the expected order value by more than the $0.02 tolerance."; action = "Compare checkout totals, discounts, and the processor charge, then recover an undercharge or refund an overcharge."; }
            case "DUPLICATE_CHARGE" -> { cause = "More than one settled charge was found for " + order + ", so the customer may have been charged twice."; action = "Review transaction references and refund the duplicate charge after confirming settlement."; }
            case "CANCELLED_BUT_CHARGED" -> { cause = order + " is cancelled but still has a settled charge for " + amount + "."; action = "Verify fulfilment did not occur and issue a refund if the charge is not valid."; }
            case "STATUS_CONFLICT" -> { cause = order + " is marked completed, but its payment has not settled successfully."; action = "Confirm settlement with the processor before fulfilment and update the order or retry payment as appropriate."; }
            case "PARTIAL_REFUND" -> { cause = order + " is marked refunded, but the refund is smaller than the original charge."; action = "Compare refund transactions with the original charge and issue the remaining refund if required."; }
            case "STALE_STATUS" -> { cause = "Payment records show a full refund for " + order + ", while the order status is not refunded."; action = "Update the order status and confirm downstream revenue reporting reflects the refund."; }
            case "CURRENCY_MISMATCH" -> { cause = order + " uses different currency codes on the order and payment records, so the values cannot be safely compared."; action = "Verify the original transaction currency and correct the source metadata before making a financial adjustment."; }
            default -> { cause = "The reconciliation engine found an inconsistency for " + order + " involving " + amount + "."; action = "Review the linked source records and resolve the underlying data or settlement issue."; }
        }
        return Map.of("available", true, "source", "deterministic-fallback", "model", "deterministic-fallback", "temperature", 0, "likelyCause", cause, "recommendedAction", action, "confidence", "rule-based", "note", reason);
    }
}