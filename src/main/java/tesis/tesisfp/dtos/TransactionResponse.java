package tesis.tesisfp.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tesis.tesisfp.entities.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionResponse {
    private String id;
    private String orderCode;
    private String paymentMethodId;
    private String paymentMethodName;
    private BigDecimal amount;
    private TransactionStatus status;
    private String referenceNumber;
    private String description;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    // For displaying masked card info
    private String maskedCardNumber;
    private String cardType;
}
