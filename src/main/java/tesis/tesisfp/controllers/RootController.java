package tesis.tesisfp.controllers;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Controlador para la ruta raíz y endpoints básicos
 * NO tiene @RequestMapping, por lo que mapea directamente a "/"
 */
@RestController
public class RootController {

    @Value("${spring.application.name:Microservicio-Pagos}")
    private String serviceName;

    @Value("${server.port:8082}")
    private String serverPort;

    @Value("${app.base.url:http://localhost:8082}")
    private String baseUrl;

    /**
     * Endpoint para la ruta raíz "/"
     * Este endpoint se ejecutará cuando accedas a tu URL de ngrok
     */
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> home() {
        Map<String, Object> response = new HashMap<>();
        response.put("🎉", "¡Microservicio de Pagos funcionando!");
        response.put("service", serviceName);
        response.put("status", "ONLINE");
        response.put("port", serverPort);
        response.put("timestamp", LocalDateTime.now());
        response.put("version", "1.0.0");
        response.put("ngrok_url", baseUrl);

        // Enlaces útiles
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("Health Check", "/health");
        endpoints.put("API Payments", "/api/payments");
        endpoints.put("Webhook", "/api/payments/webhook");
        endpoints.put("Test Webhook", "/api/payments/webhook/test");
        endpoints.put("Payment Methods", "/api/payments/methods");
        endpoints.put("H2 Console", "/h2-console");
        endpoints.put("Swagger UI", "/swagger-ui.html");

        response.put("available_endpoints", endpoints);

        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", serviceName);
        health.put("timestamp", LocalDateTime.now());
        health.put("database", "H2 - Connected");
        health.put("mercadopago", "Configured");
        health.put("ngrok", "Active");

        return ResponseEntity.ok(health);
    }

    /**
     * Test específico para ngrok
     */
    @GetMapping("/ngrok-test")
    public ResponseEntity<Map<String, Object>> ngrokTest() {
        Map<String, Object> test = new HashMap<>();
        test.put("✅", "ngrok está funcionando correctamente");
        test.put("service", serviceName);
        test.put("external_url", baseUrl);
        test.put("ready_for_webhooks", true);
        test.put("timestamp", LocalDateTime.now());
        test.put("next_steps", new String[]{
                "1. Configura webhook en Mercado Pago",
                "2. URL webhook: " + baseUrl + "/api/payments/webhook",
                "3. Prueba un pago desde el frontend"
        });

        return ResponseEntity.ok(test);
    }
}