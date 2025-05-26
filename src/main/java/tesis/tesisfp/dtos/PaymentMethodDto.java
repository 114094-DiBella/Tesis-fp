package tesis.tesisfp.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentMethodDto {
    private String id;
    private String name;
    private String type;
    private String description;
    private BigDecimal commission;
    private Integer processingDays;
    private Boolean active;
}