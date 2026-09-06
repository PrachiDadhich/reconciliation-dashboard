package com.ledgermatch.reconciliation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class ReconciliationEngine {
    public static final BigDecimal TOLERANCE = new BigDecimal("0.02");
    public record Order(UUID id,String orderId,String email,String currency,BigDecimal netAmount,String status) {}
    public record Payment(UUID id,String transactionRef,String orderRef,String currency,BigDecimal amount,String type,String status) {}
    public record Finding(String type,String severity,UUID orderId,UUID paymentId,BigDecimal amountAtRisk,Map<String,Object> details) {}
    public record Result(List<Finding> findings,BigDecimal reconciledValue,BigDecimal disputedValue,BigDecimal moneyAtRisk) {}

    public Result reconcile(List<Order> orders,List<Payment> payments) {
        Map<String,List<Payment>> groups=new TreeMap<>(); for(Payment p:payments) groups.computeIfAbsent(norm(p.orderRef()), k->new ArrayList<>()).add(p);
        Set<String> matched=new HashSet<>(); List<Finding> findings=new ArrayList<>(); BigDecimal reconciled=BigDecimal.ZERO, risk=BigDecimal.ZERO;
        for(Order o:orders){ List<Payment> group=groups.getOrDefault(norm(o.orderId()),List.of()); if(group.isEmpty()){var f=f("MISSING_PAYMENT","critical",o.id(),null,o.netAmount(),Map.of("orderId",o.orderId()));findings.add(f);risk=risk.add(o.netAmount());continue;} matched.add(norm(o.orderId()));
            Payment first=group.getFirst(); BigDecimal charges=sum(group,"charge"), refunds=sum(group,"refund"); boolean settledCharge=group.stream().anyMatch(p->p.type().equals("charge")&&p.status().equals("settled"));
            if(group.stream().anyMatch(p->!o.currency().equals(p.currency()))) findings.add(f("CURRENCY_MISMATCH","high",o.id(),first.id(),o.netAmount(),Map.of("orderCurrency",o.currency(),"paymentCurrency",first.currency())));
            long settledCharges=group.stream().filter(p->p.type().equals("charge")&&p.status().equals("settled")).count();
            if(settledCharges>1){BigDecimal over=charges.subtract(o.netAmount()).max(BigDecimal.ZERO); findings.add(f("DUPLICATE_CHARGE","critical",o.id(),first.id(),over,Map.of("chargeCount",settledCharges,"chargeTotal",charges)));risk=risk.add(over);}
            if(o.status().equalsIgnoreCase("cancelled")&&settledCharge){findings.add(f("CANCELLED_BUT_CHARGED","critical",o.id(),first.id(),charges,Map.of("chargeTotal",charges)));risk=risk.add(charges);}
            if(o.status().equalsIgnoreCase("completed")&&group.stream().anyMatch(p->p.type().equals("charge")&&!p.status().equals("settled"))){findings.add(f("STATUS_CONFLICT","high",o.id(),first.id(),o.netAmount(),Map.of("paymentStatuses",group.stream().map(Payment::status).distinct().toList())));risk=risk.add(o.netAmount());}
            if(o.status().equalsIgnoreCase("refunded")&&charges.subtract(refunds).compareTo(TOLERANCE)>0){BigDecimal shortfall=charges.subtract(refunds);findings.add(f("PARTIAL_REFUND","high",o.id(),first.id(),shortfall,Map.of("chargeTotal",charges,"refundTotal",refunds)));risk=risk.add(shortfall);}
            if(!o.status().equalsIgnoreCase("refunded")&&refunds.add(TOLERANCE).compareTo(charges)>=0&&refunds.compareTo(BigDecimal.ZERO)>0){findings.add(f("STALE_STATUS","high",o.id(),first.id(),refunds,Map.of("chargeTotal",charges,"refundTotal",refunds)));risk=risk.add(refunds);}
            if(group.size()==1&&first.type().equals("charge")&&first.status().equals("settled")&&o.currency().equals(first.currency())&&o.status().equalsIgnoreCase("completed")){
                BigDecimal difference=o.netAmount().subtract(first.amount()).abs(); if(difference.compareTo(TOLERANCE)>0){BigDecimal uncollected=o.netAmount().subtract(first.amount()).max(BigDecimal.ZERO); findings.add(f("AMOUNT_MISMATCH","critical",o.id(),first.id(),uncollected,Map.of("orderAmount",o.netAmount(),"paymentAmount",first.amount())));risk=risk.add(uncollected);} else reconciled=reconciled.add(o.netAmount());
            } else if(findings.stream().noneMatch(x->Objects.equals(x.orderId(),o.id()))) reconciled=reconciled.add(o.netAmount());
        }
        for(var entry:groups.entrySet()) if(!matched.contains(entry.getKey())) for(Payment p:entry.getValue()){if(p.type().equals("charge")){var f=f("ORPHAN_PAYMENT","critical",null,p.id(),p.amount(),Map.of("orderReference",p.orderRef()));findings.add(f);risk=risk.add(p.amount());}}
        BigDecimal disputed=findings.stream().map(Finding::amountAtRisk).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new Result(List.copyOf(findings),money(reconciled),money(disputed),money(risk));
    }
    private static Finding f(String type,String severity,UUID order,UUID payment,BigDecimal amount,Map<String,Object> details){return new Finding(type,severity,order,payment,money(amount),details);}
    private static BigDecimal sum(List<Payment> p,String type){return p.stream().filter(x->x.type().equals(type)&&x.status().equals("settled")).map(Payment::amount).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private static String norm(String s){return s==null?null:s.trim().toUpperCase(Locale.ROOT);}
    private static BigDecimal money(BigDecimal n){return n.setScale(2,RoundingMode.HALF_UP);}
}