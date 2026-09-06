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
    private final JdbcTemplate db; private final ObjectMapper json; private final RestClient client; private final String apiKey;
    public ExplanationController(JdbcTemplate db,ObjectMapper json,@Value("${app.openai-api-key:}")String apiKey){this.db=db;this.json=json;this.apiKey=apiKey;this.client=RestClient.builder().baseUrl("https://api.openai.com/v1").build();}
    @PostMapping("/discrepancies/{id}/explain") public Map<String,Object> explain(@PathVariable UUID id){UUID user=CurrentUser.id(); List<Map<String,Object>> cached=db.query("select model,temperature,likely_cause,recommended_action,confidence from discrepancy_explanations e join discrepancies d on d.id=e.discrepancy_id where e.discrepancy_id=? and d.user_id=?",(rs,n)->{Map<String,Object> row=new LinkedHashMap<>();row.put("model",rs.getString("model"));row.put("temperature",rs.getBigDecimal("temperature"));row.put("likelyCause",rs.getString("likely_cause"));row.put("recommendedAction",rs.getString("recommended_action"));row.put("confidence",rs.getString("confidence"));return row;},id,user); if(!cached.isEmpty())return cached.getFirst();
        var rows=db.query("select d.type,d.severity,d.amount_at_risk,d.details,o.order_id,o.customer_email from discrepancies d left join orders o on o.id=d.order_id and o.user_id=d.user_id where d.id=? and d.user_id=?",(rs,n)->Map.of("type",rs.getString("type"),"severity",rs.getString("severity"),"amount",rs.getBigDecimal("amount_at_risk"),"details",rs.getString("details"),"orderId",String.valueOf(rs.getString("order_id"))),id,user); if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND); if(apiKey.isBlank())return Map.of("available",false,"message","explanation unavailable - the numbers above are unaffected");
        String prompt="Explain this deterministic reconciliation discrepancy. Return only JSON with likely_cause, recommended_action, confidence. Facts: "+rows.getFirst(); JsonNode answer=null; for(int attempt=0;attempt<2&&answer==null;attempt++){try{String body=json.writeValueAsString(Map.of("model","gpt-4o-mini","temperature",0.2,"messages",List.of(Map.of("role","system","content","You explain accounting exceptions concisely."),Map.of("role","user","content",prompt)),"response_format",Map.of("type","json_object")));String raw=client.post().uri("/chat/completions").header("Authorization","Bearer "+apiKey).header("Content-Type","application/json").body(body).retrieve().body(String.class);JsonNode root=json.readTree(raw);String content=root.path("choices").path(0).path("message").path("content").asText();JsonNode parsed=json.readTree(content);if(parsed.has("likely_cause")&&parsed.get("likely_cause").isTextual()&&parsed.has("recommended_action")&&parsed.get("recommended_action").isTextual()&&parsed.has("confidence")&&parsed.get("confidence").isTextual())answer=parsed;}catch(Exception ignored){}}
        if(answer==null)return Map.of("available",false,"message","explanation unavailable - the numbers above are unaffected"); try{Map<String,Object> out=Map.of("available",true,"model","gpt-4o-mini","temperature",0.2,"likelyCause",answer.get("likely_cause").asText(),"recommendedAction",answer.get("recommended_action").asText(),"confidence",answer.get("confidence").asText());db.update("insert into discrepancy_explanations(discrepancy_id,model,temperature,likely_cause,recommended_action,confidence,raw_response) values (?,?,?,?,?,?,?::jsonb)",id,"gpt-4o-mini",0.2,answer.get("likely_cause").asText(),answer.get("recommended_action").asText(),answer.get("confidence").asText(),answer.toString());return out;}catch(Exception e){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Could not cache explanation",e);}
    }
}