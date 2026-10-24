package io.aegis.inventory.config;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
@ConfigurationProperties("inventory.reservation")
public record ReservationSettings(@DefaultValue("30s") Duration ttl){
 public ReservationSettings{if(ttl==null||ttl.isZero()||ttl.isNegative())throw new IllegalArgumentException("positive reservation TTL required");}
}
