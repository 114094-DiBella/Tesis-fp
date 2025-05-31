package tesis.tesisfp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import tesis.tesisfp.dtos.PaymentRequest;
import tesis.tesisfp.dtos.PaymentMethodDto;
import tesis.tesisfp.dtos.TransactionResponse;
import tesis.tesisfp.services.PaymentService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "http://localhost:4200")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    @Value("${mercadopago.webhook.secret:your-webhook-secret}")
    private String webhookSecret;

    @Autowired
    private PaymentService paymentService;

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

    @GetMapping("/methods")
    public ResponseEntity<List<PaymentMethodDto>> getPaymentMethods() {
        try {
            List<PaymentMethodDto> methods = paymentService.getAvailablePaymentMethods();
            return ResponseEntity.ok(methods);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

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
     * Webhook simplificado para recibir notificaciones de Mercado Pago
     * Maneja tanto GET (verificación) como POST (notificaciones)
     */
    @RequestMapping(value = "/webhook", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> handleWebhook(HttpServletRequest request) {

        String method = request.getMethod();
        logger.info("Webhook request recibido - Método: {}", method);

        try {
            // Si es GET, es solo verificación
            if ("GET".equals(method)) {
                logger.info("Webhook verificado con GET");
                return ResponseEntity.ok("Webhook is working");
            }

            // Si es POST, procesar la notificación
            if ("POST".equals(method)) {
                // Leer el cuerpo de la petición
                String payload = getRequestBody(request);
                logger.info("Payload recibido: {}", payload);

                // Headers importantes
                String signature = request.getHeader("x-signature");
                String requestId = request.getHeader("x-request-id");

                logger.info("Request ID: {}, Signature: {}", requestId, signature);

                // Procesar el payload si no está vacío
                if (payload != null && !payload.trim().isEmpty()) {
                    processWebhookPayload(payload);
                } else {
                    logger.warn("Payload vacío en webhook");
                }
            }

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            logger.error("Error procesando webhook: {}", e.getMessage(), e);
            return ResponseEntity.ok("OK"); // Siempre devolver 200 para MP
        }
    }

    /**
     * Procesar el contenido del webhook
     */
    private void processWebhookPayload(String payload) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> payloadMap = mapper.readValue(payload, Map.class);

            String type = (String) payloadMap.get("type");
            logger.info("Tipo de notificación: {}", type);

            if ("payment".equals(type)) {
                Map<String, Object> data = (Map<String, Object>) payloadMap.get("data");
                if (data != null && data.get("id") != null) {
                    String paymentId = data.get("id").toString();

                    logger.info("Procesando pago ID: {}", paymentId);

                    // Actualizar estado del pago
                    TransactionResponse transaction = paymentService.getPaymentStatus(paymentId);
                    logger.info("Transacción: {} - Estado: {}",
                            transaction.getOrderCode(), transaction.getStatus());

                    // Notificar al servicio de ventas
                    notifyVentasServiceAsync(transaction);
                }
            }

        } catch (Exception e) {
            logger.error("Error procesando payload del webhook: {}", e.getMessage(), e);
        }
    }

    /**
     * Leer el cuerpo de la petición HTTP
     */
    private String getRequestBody(HttpServletRequest request) {
        try {
            StringBuilder buffer = new StringBuilder();
            BufferedReader reader = request.getReader();
            String line;

            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }

            return buffer.toString();

        } catch (Exception e) {
            logger.error("Error leyendo cuerpo de la petición: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Notificar al servicio de ventas de forma asíncrona
     */
    private void notifyVentasServiceAsync(TransactionResponse transaction) {
        // Ejecutar en un hilo separado para no bloquear la respuesta del webhook
        new Thread(() -> {
            try {
                String status = "";
                if ("APPROVED".equals(transaction.getStatus().toString())) {
                    status = "PAGADA";
                } else if ("REJECTED".equals(transaction.getStatus().toString())) {
                    status = "RECHAZADA";
                } else {
                    logger.info("Estado no procesable: {}", transaction.getStatus());
                    return;
                }

                notifyVentasService(transaction.getOrderCode(), status);

            } catch (Exception e) {
                logger.error("Error en notificación asíncrona: {}", e.getMessage());
            }
        }).start();
    }

    /**
     * Endpoint de verificación específico
     */
    @GetMapping("/webhook/verify")
    public ResponseEntity<Map<String, String>> verifyWebhook() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "Webhook endpoint is working");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

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

    private void notifyVentasService(String facturaId, String status) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "http://localhost:8081/api/facturas/" + facturaId + "/update-status";

            Map<String, String> request = new HashMap<>();
            request.put("status", status);

            logger.info("Notificando a servicio de ventas: {} -> {}", facturaId, status);
            restTemplate.put(url, request);
            logger.info("Servicio de ventas notificado exitosamente");

        } catch (Exception e) {
            logger.error("Error notificando a servicio de ventas: {}", e.getMessage(), e);
        }
    }
}