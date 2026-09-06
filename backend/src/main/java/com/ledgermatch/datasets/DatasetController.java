package com.ledgermatch.datasets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgermatch.security.CurrentUser;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController @RequestMapping("/datasets")
public class DatasetController {
    private static final Logger log = LoggerFactory.getLogger(DatasetController.class);
    private static final DateTimeFormatter PAYMENT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate db; private final ObjectMapper json;
    public DatasetController(JdbcTemplate db, ObjectMapper json) { this.db=db; this.json=json; }
    @PostMapping(value="/orders", consumes="multipart/form-data") public Map<String,Object> orders(@RequestPart("file") MultipartFile file) { return importOrders(file); }
    @PostMapping(value="/payments", consumes="multipart/form-data") public Map<String,Object> payments(@RequestPart("file") MultipartFile file) { return importPayments(file); }

    private Map<String,Object> importOrders(MultipartFile file) {
        UUID user=CurrentUser.id(), batch=UUID.randomUUID(); Set<String> seen=new HashSet<>(); List<String> warnings=new ArrayList<>(); int count=0;
        try (var reader=new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            var records=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader);
            db.update("insert into dataset_batches(id,user_id,kind,file_name,row_count,warnings) values (?,?, 'orders',?,0,?::jsonb)",batch,user,file.getOriginalFilename(),"[]");
            for (CSVRecord r: records) { String raw=r.toString(); String id=required(r,"order_id"); String norm=normalize(id); if(!seen.add(raw)){warnings.add("Exact duplicate row: "+id);continue;} if(!id.equals(norm)) warnings.add("Normalized order id: "+id); if(blank(r,"customer_email")) warnings.add("Blank customer email: "+id); if(blank(r,"discount")) warnings.add("Blank discount: "+id);
                db.update("insert into orders(id,user_id,batch_id,order_id,order_id_norm,order_date,customer_email,currency,gross_amount,discount,net_amount,status) values (?,?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),user,batch,id,norm,date(r.get("order_date"),false),nullable(r.get("customer_email")),required(r,"currency"),decimal(r,"gross_amount"),blank(r,"discount")?BigDecimal.ZERO:decimal(r,"discount"),decimal(r,"net_amount"),required(r,"status")); count++; }
            updateBatch(batch,count,warnings); return result(batch,count,warnings);
        } catch(Exception e){ log.error("Orders CSV ingestion failed", e); throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Could not parse orders CSV: "+e.getMessage(),e); }
    }
    private Map<String,Object> importPayments(MultipartFile file) {
        UUID user=CurrentUser.id(), batch=UUID.randomUUID(); Set<String> seen=new HashSet<>(); List<String> warnings=new ArrayList<>(); int count=0;
        try (var reader=new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            var records=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader);
            db.update("insert into dataset_batches(id,user_id,kind,file_name,row_count,warnings) values (?,?, 'payments',?,0,?::jsonb)",batch,user,file.getOriginalFilename(),"[]");
            for (CSVRecord r: records) { String raw=r.toString(); String ref=required(r,"order_reference"); String norm=ref.isBlank()?null:normalize(ref); if(!seen.add(raw)){warnings.add("Exact duplicate row: "+r.get("transaction_ref"));continue;} if(!ref.equals(norm)) warnings.add("Normalized order reference: "+ref); if(blank(r,"processed_at")) warnings.add("Missing processed timestamp: "+r.get("transaction_ref"));
                db.update("insert into payments(id,user_id,batch_id,transaction_ref,order_reference,order_ref_norm,processed_at,currency,amount,fee,net_settled,type,status) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),user,batch,required(r,"transaction_ref"),nullable(r.get("order_reference")),norm,date(r.get("processed_at"),true),required(r,"currency"),decimal(r,"amount"),blank(r,"fee")?BigDecimal.ZERO:decimal(r,"fee"),blank(r,"net_settled")?null:decimal(r,"net_settled"),required(r,"type"),required(r,"status")); count++; }
            updateBatch(batch,count,warnings); return result(batch,count,warnings);
        } catch(Exception e){ log.error("Payments CSV ingestion failed", e); throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Could not parse payments CSV: "+e.getMessage(),e); }
    }
    private void updateBatch(UUID id,int count,List<String> warnings)throws Exception { db.update("update dataset_batches set row_count=?,warnings=?::jsonb where id=?",count,json.writeValueAsString(warnings),id); }
    private Map<String,Object> result(UUID id,int count,List<String> warnings){return Map.of("batchId",id,"rowCount",count,"warnings",warnings);}
    private static String normalize(String value){return value==null?null:value.trim().toUpperCase(Locale.ROOT);}
    private static boolean blank(CSVRecord r,String key){return !r.isMapped(key)||r.get(key)==null||r.get(key).isBlank();}
    private static String required(CSVRecord r,String key){if(blank(r,key))throw new IllegalArgumentException("Missing required field "+key);return r.get(key).trim();}
    private static String nullable(String value){return value==null||value.isBlank()?null:value.trim();}
    private static BigDecimal decimal(CSVRecord r,String key){return new BigDecimal(required(r,key));}
    private static OffsetDateTime date(String value,boolean payment){if(value==null||value.isBlank())return null; LocalDateTime parsed=payment?LocalDateTime.parse(value.trim(),PAYMENT_DATE):LocalDateTime.parse(value.trim().replace("Z",""),ORDER_DATE); return parsed.atOffset(ZoneOffset.UTC);}
}