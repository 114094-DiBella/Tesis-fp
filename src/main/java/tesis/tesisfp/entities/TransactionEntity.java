package tesis.tesisfp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String orderCode; // Reference to the order

    @Column(nullable = false)
    private String paymentMethodId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status = TransactionStatus.PENDING;

    private String referenceNumber; // For cards or transfers
    private String description;
    private String rejectionReason;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;

    // For cards
    private String maskedCardNumber; // **** **** **** 1234
    private String cardType; // VISA, MASTERCARD, etc.

}
