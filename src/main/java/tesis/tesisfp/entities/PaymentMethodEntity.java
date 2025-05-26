package tesis.tesisfp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_methods")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentMethodEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name; // "Credit Card", "Cash", "Bank Transfer"

    @Column(nullable = false)
    private String type; // "CARD", "CASH", "TRANSFER", "DIGITAL_WALLET"

    @Column(nullable = false)
    private Boolean active = true;

    private String description;
    private BigDecimal commission = BigDecimal.ZERO; // Payment method commission
    private Integer processingDays = 0; // Days to process the payment

    @CreationTimestamp
    private LocalDateTime createdAt;
}
