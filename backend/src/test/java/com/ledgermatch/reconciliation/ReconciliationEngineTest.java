package com.ledgermatch.reconciliation;

import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReconciliationEngineTest {
    @Test void suppliedFixturesProduceExpectedTaxonomy() throws Exception {
        List<ReconciliationEngine.Order> orders=new ArrayList<>(); Set<String> orderRows=new HashSet<>();
        try(var records=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(java.nio.file.Files.newBufferedReader(Path.of("..","fixtures","orders.csv")))){
            for(var r:records) if(orderRows.add(r.toMap().toString())) orders.add(new ReconciliationEngine.Order(UUID.nameUUIDFromBytes(r.get("order_id").getBytes()),r.get("order_id"),r.get("customer_email"),r.get("currency"),new BigDecimal(r.get("net_amount")),r.get("status")));
        }
        List<ReconciliationEngine.Payment> payments=new ArrayList<>(); Set<String> paymentRows=new HashSet<>();
        try(var records=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(java.nio.file.Files.newBufferedReader(Path.of("..","fixtures","payments.csv")))){
            for(var r:records) if(paymentRows.add(r.toMap().toString())) payments.add(new ReconciliationEngine.Payment(UUID.nameUUIDFromBytes(r.get("transaction_ref").getBytes()),r.get("transaction_ref"),r.get("order_reference"),r.get("currency"),new BigDecimal(r.get("amount")),r.get("type"),r.get("status")));
        }
        var result=new ReconciliationEngine().reconcile(orders,payments); Map<String,Long> counts=new HashMap<>(); result.findings().forEach(f->counts.merge(f.type(),1L,Long::sum));
        assertEquals(184,orders.size()); assertEquals(187,payments.size());
        assertEquals(4L,counts.get("MISSING_PAYMENT")); assertEquals(3L,counts.get("ORPHAN_PAYMENT")); assertEquals(3L,counts.get("AMOUNT_MISMATCH"));
        assertEquals(2L,counts.get("DUPLICATE_CHARGE")); assertEquals(1L,counts.get("CANCELLED_BUT_CHARGED")); assertEquals(2L,counts.get("STATUS_CONFLICT"));
        assertEquals(1L,counts.get("PARTIAL_REFUND")); assertEquals(1L,counts.get("STALE_STATUS")); assertEquals(2L,counts.get("CURRENCY_MISMATCH"));
        assertEquals(19, result.findings().size());
    }
}