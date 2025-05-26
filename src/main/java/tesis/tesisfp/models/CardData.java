package tesis.tesisfp.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CardData {
    @NotBlank
    @Size(min = 16, max = 19)
    private String number;

    @NotBlank
    @Size(min = 2, max = 2)
    private String expiryMonth;

    @NotBlank
    @Size(min = 4, max = 4)
    private String expiryYear;

    @NotBlank
    @Size(min = 3, max = 4)
    private String cvv;

    @NotBlank
    private String holderName;
}
