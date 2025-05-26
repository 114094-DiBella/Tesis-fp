package tesis.tesisfp.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tesis.tesisfp.models.CardData;
import tesis.tesisfp.models.TransferData;

import java.math.BigDecimal;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRequest {
    @NotBlank
    private String orderCode;

    @NotBlank
    private String paymentMethodId;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    private String description;

    // For card payments
    private CardData cardData;

    // For transfers
    private TransferData transferData;
}