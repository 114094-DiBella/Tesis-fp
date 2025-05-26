package tesis.tesisfp.models;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransferData {
    @NotBlank
    private String accountNumber;

    @NotBlank
    private String accountType; // "SAVINGS", "CHECKING"

    @NotBlank
    private String bankName;

    @NotBlank
    private String holderName;

    @NotBlank
    private String holderDocument;

    private String concept;
}
