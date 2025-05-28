package tesis.tesisfp.controllers;

import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tesis.tesisfp.dtos.PaymentRequest;
import tesis.tesisfp.dtos.PaymentMethodDto;
import tesis.tesisfp.dtos.TransactionResponse;
import tesis.tesisfp.services.PaymentService;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "http://localhost:4200")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    /**
     * Crea una preferencia de pago para Checkout Bricks
     */
    @PostMapping("/create-preference")
    public ResponseEntity<?> createPaymentPreference(@Valid @RequestBody PaymentRequest request) {
        try {
            String preferenceId = paymentService.createPaymentPreference(request);
            return ResponseEntity.ok(Map.of(
                    "preferenceId", preferenceId,
                    "message", "Preferencia creada exitosamente"
            ));
        } catch (MPException | MPApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Error al crear preferencia: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }

    /**
     * Procesa el pago enviado desde el frontend
     */
    @PostMapping("/process")
    public ResponseEntity<?> processPayment(@Valid @RequestBody PaymentRequest request) {
        try {
            TransactionResponse response = paymentService.processPayment(request);
            return ResponseEntity.ok(response);
        } catch (MPException | MPApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Error al procesar pago: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }

    /**
     * Consulta el estado de un pago
     */
    @GetMapping("/status/{paymentId}")
    public ResponseEntity<?> getPaymentStatus(@PathVariable String paymentId) {
        try {
            TransactionResponse response = paymentService.getPaymentStatus(paymentId);
            return ResponseEntity.ok(response);
        } catch (MPException | MPApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Error al consultar estado: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Pago no encontrado"));
        }
    }

    /**
     * Obtiene métodos de pago disponibles
     */
    @GetMapping("/methods")
    public ResponseEntity<List<PaymentMethodDto>> getPaymentMethods() {
        try {
            List<PaymentMethodDto> methods = paymentService.getAvailablePaymentMethods();
            return ResponseEntity.ok(methods);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Obtiene transacciones por código de orden
     */
    @GetMapping("/order/{orderCode}")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByOrder(@PathVariable String orderCode) {
        try {
            List<TransactionResponse> transactions = paymentService.getTransactionsByOrder(orderCode);
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Webhook para recibir notificaciones de Mercado Pago
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody Map<String, Object> payload) {
        try {
            // Procesar notificación de MP
            String type = (String) payload.get("type");

            if ("payment".equals(type)) {
                Map<String, Object> data = (Map<String, Object>) payload.get("data");
                String paymentId = (String) data.get("id");

                // Actualizar estado del pago
                paymentService.getPaymentStatus(paymentId);
            }

            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            return ResponseEntity.ok("OK"); // Siempre responder OK a MP
        }
    }

    /**
     * Endpoint para manejar respuestas de checkout
     */
    @GetMapping("/success")
    public ResponseEntity<?> paymentSuccess(@RequestParam(required = false) String payment_id,
                                            @RequestParam(required = false) String external_reference) {
        try {
            if (payment_id != null) {
                TransactionResponse response = paymentService.getPaymentStatus(payment_id);
                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "transaction", response
                ));
            }
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al procesar respuesta"));
        }
    }

    @GetMapping("/failure")
    public ResponseEntity<?> paymentFailure() {
        return ResponseEntity.ok(Map.of("status", "failure", "message", "Pago rechazado"));
    }

    @GetMapping("/pending")
    public ResponseEntity<?> paymentPending() {
        return ResponseEntity.ok(Map.of("status", "pending", "message", "Pago pendiente"));
    }



}