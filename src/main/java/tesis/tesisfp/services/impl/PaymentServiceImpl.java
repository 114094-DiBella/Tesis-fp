package tesis.tesisfp.services.impl;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
import com.mercadopago.resources.payment.Payment;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tesis.tesisfp.controllers.PaymentController;
import tesis.tesisfp.dtos.PaymentRequest;
import tesis.tesisfp.dtos.PaymentMethodDto;
import tesis.tesisfp.dtos.TransactionResponse;
import tesis.tesisfp.entities.PaymentMethodEntity;
import tesis.tesisfp.entities.TransactionEntity;
import tesis.tesisfp.entities.TransactionStatus;
import tesis.tesisfp.repositories.PaymentJpaRepository;
import tesis.tesisfp.repositories.TransactionJpaRepository;
import tesis.tesisfp.services.PaymentService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.mysql.cj.conf.PropertyKey.logger;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Value("${mercadopago.access.token}")
    private String accessToken;

    @Value("${app.base.url}")
    private String baseUrl;

    @Value("${mercadopago.webhook.secret}")
    private String webhookSecret;


    @Autowired
    private PaymentJpaRepository paymentMethodRepository;

    @Autowired
    private TransactionJpaRepository transactionRepository;

    @Autowired
    private ModelMapper modelMapper;

    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceImpl.class);

    /**
     * Crea una preferencia de pago para Checkout Bricks
     */
    @Override
    @Transactional
    public String createPaymentPreference(PaymentRequest request) throws MPException, MPApiException {

        MercadoPagoConfig.setAccessToken(accessToken);

        // URLs de retorno
        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(baseUrl + "/api/payments/success")
                .pending(baseUrl + "/api/payments/pending")
                .failure(baseUrl + "/api/payments/failure")
                .build();

        // Item de la preferencia
        PreferenceItemRequest itemRequest = PreferenceItemRequest.builder()
                .id(request.getOrderCode())
                .title(request.getProductName() != null ? request.getProductName() : "Producto")
                .description(request.getDescription() != null ? request.getDescription() : "Pago de orden")
                .quantity(request.getQuantity() != null ? request.getQuantity() : 1)
                .currencyId("ARS")
                .unitPrice(request.getAmount())
                .build();

        List<PreferenceItemRequest> items = new ArrayList<>();
        items.add(itemRequest);

        // Configuración de la preferencia - AQUÍ ESTÁ LA CLAVE
        PreferenceRequest preferenceRequest = PreferenceRequest.builder()
                .items(items)
                .backUrls(backUrls)
                .autoReturn("approved") // Opcional: redirecciona automáticamente si es aprobado
                .externalReference(request.getOrderCode()) // MUY IMPORTANTE
                .notificationUrl(baseUrl + "/api/payments/webhook") // ESTO ES LO QUE TE FALTABA
                .statementDescriptor("TU_TIENDA") // Aparece en el resumen de tarjeta
                .expires(true)
                .build();

        // Crear transacción pendiente ANTES de crear la preferencia
        TransactionEntity transaction = createPendingTransaction(request, null);

        // Crear preferencia
        PreferenceClient client = new PreferenceClient();
        Preference preference = client.create(preferenceRequest);

        // Actualizar transacción con el ID de preferencia
        transaction.setReferenceNumber(preference.getId());
        transactionRepository.save(transaction);

        logger.info("Preferencia creada - ID: {}, Webhook URL: {}",
                preference.getId(), baseUrl + "/api/payments/webhook");

        return preference.getSandboxInitPoint();
    }
    /**
     * Procesa un pago usando la información del brick
     */
    @Override
    @Transactional
    public TransactionResponse processPayment(PaymentRequest request) throws MPException, MPApiException {
        MercadoPagoConfig.setAccessToken(accessToken);

        try {
            // Buscar transacción existente (la más reciente)
            TransactionEntity transaction = transactionRepository
                    .findFirstByOrderCodeOrderByCreatedAtDesc(request.getOrderCode())
                    .orElse(createNewTransaction(request));

            // Actualizar estado a procesando
            transaction.setStatus(TransactionStatus.PROCESSING);
            transaction.setProcessedAt(LocalDateTime.now());
            transactionRepository.save(transaction);

            // Si es pago en efectivo, marcar como aprobado automáticamente
            if ("CASH".equals(request.getPaymentMethodId())) {
                return processEffectivoPayment(transaction, request);
            }

            // Para otros métodos, usar la API de MP
            // Aquí procesarías el pago con los datos del brick

            return convertToResponse(transaction);

        } catch (Exception e) {
            // Marcar transacción como rechazada
            markTransactionAsRejected(request.getOrderCode(), e.getMessage());
            throw e;
        }
    }

    /**
     * Verifica el estado de un pago
     */
    @Override
    public TransactionResponse getPaymentStatus(String paymentId) throws MPException, MPApiException {
        logger.info("Consultando estado del pago: {}", paymentId);

        // Validación inicial
        if (paymentId == null || paymentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Payment ID no puede estar vacío");
        }

        MercadoPagoConfig.setAccessToken(accessToken);

        try {
            // Validar que el paymentId sea numérico
            Long paymentIdLong;
            try {
                paymentIdLong = Long.valueOf(paymentId);
            } catch (NumberFormatException e) {
                logger.error("Payment ID inválido (no numérico): {}", paymentId);
                throw new IllegalArgumentException("Payment ID debe ser numérico: " + paymentId);
            }

            // Consultar el pago en MercadoPago
            PaymentClient client = new PaymentClient();
            Payment payment = client.get(paymentIdLong);

            if (payment == null) {
                logger.error("Pago no encontrado en MercadoPago: {}", paymentId);
                throw new RuntimeException("Pago no encontrado en MercadoPago: " + paymentId);
            }

            logger.info("Pago consultado exitosamente - ID: {}, Estado: {}, External Reference: {}",
                    payment.getId(), payment.getStatus(), payment.getExternalReference());

            // Buscar transacción en base de datos
            // Primero intentar por reference number, luego por order code
            TransactionEntity transaction = findTransactionByPayment(payment, paymentId);

            if (transaction == null) {
                logger.warn("Transacción no encontrada en BD para payment ID: {}", paymentId);
                // Crear una nueva transacción si no existe
                transaction = createTransactionFromPayment(payment, paymentId);
            }

            // Actualizar estado según respuesta de MP
            updateTransactionFromPayment(transaction, payment);

            return convertToResponse(transaction);

        } catch (MPApiException e) {
            logger.error("Error de API de MercadoPago consultando pago {}: Status: {}, Message: {}",
                    paymentId, e.getStatusCode(), e.getMessage());

            // Si es error 404, el pago no existe
            if (e.getStatusCode() == 404) {
                markTransactionAsRejected(paymentId, "Pago no encontrado en MercadoPago");
                throw new RuntimeException("Pago no encontrado: " + paymentId);
            }

            // Para otros errores, marcar como rechazado
            markTransactionAsRejected(paymentId, "Error API MP: " + e.getMessage());
            throw e;

        } catch (MPException e) {
            logger.error("Error de MercadoPago consultando pago {}: {}", paymentId, e.getMessage());
            markTransactionAsRejected(paymentId, "Error MP: " + e.getMessage());
            throw e;

        } catch (Exception e) {
            logger.error("Error inesperado consultando pago {}: {}", paymentId, e.getMessage(), e);
            markTransactionAsRejected(paymentId, "Error inesperado: " + e.getMessage());
            throw new RuntimeException("Error consultando estado del pago: " + e.getMessage(), e);
        }
    }

    /**
     * Busca la transacción en la base de datos usando diferentes criterios
     */
    private TransactionEntity findTransactionByPayment(Payment payment, String paymentId) {
        // Intentar por reference number
        Optional<TransactionEntity> transaction = transactionRepository.findByReferenceNumber(paymentId);
        if (transaction.isPresent()) {
            return transaction.get();
        }

        // Intentar por external reference (order code)
        if (payment.getExternalReference() != null) {
            transaction = transactionRepository.findFirstByOrderCodeOrderByCreatedAtDesc(payment.getExternalReference());
            if (transaction.isPresent()) {
                return transaction.get();
            }
        }

        return null;
    }

    /**
     * Crea una nueva transacción a partir de los datos del pago de MP
     */
    private TransactionEntity createTransactionFromPayment(Payment payment, String paymentId) {
        logger.info("Creando nueva transacción para payment ID: {}", paymentId);

        TransactionEntity transaction = new TransactionEntity();
        transaction.setOrderCode(payment.getExternalReference() != null ?
                payment.getExternalReference() : "ORDER-" + paymentId);
        transaction.setPaymentMethodId(payment.getPaymentMethodId());
        transaction.setAmount(payment.getTransactionAmount());
        transaction.setReferenceNumber(paymentId);
        transaction.setDescription("Pago procesado via webhook - " + paymentId);
        transaction.setCreatedAt(LocalDateTime.now());

        // Establecer estado inicial basado en el estado del pago
        updateTransactionStatusFromPayment(transaction, payment);

        return transactionRepository.save(transaction);
    }

    /**
     * Actualiza el estado de la transacción basado en el estado del pago de MP
     */
    private void updateTransactionStatusFromPayment(TransactionEntity transaction, Payment payment) {
        String mpStatus = payment.getStatus();

        switch (mpStatus) {
            case "approved":
                transaction.setStatus(TransactionStatus.APPROVED);
                transaction.setProcessedAt(LocalDateTime.now());
                break;
            case "pending":
                transaction.setStatus(TransactionStatus.PENDING);
                break;
            case "in_process":
                transaction.setStatus(TransactionStatus.PROCESSING);
                break;
            case "rejected":
                transaction.setStatus(TransactionStatus.REJECTED);
                transaction.setRejectionReason(payment.getStatusDetail());
                transaction.setProcessedAt(LocalDateTime.now());
                break;
            case "cancelled":
                transaction.setStatus(TransactionStatus.CANCELLED);
                transaction.setProcessedAt(LocalDateTime.now());
                break;
            case "refunded":
                transaction.setStatus(TransactionStatus.REFUNDED);
                transaction.setProcessedAt(LocalDateTime.now());
                break;
            default:
                logger.warn("Estado de pago no reconocido: {}", mpStatus);
                transaction.setStatus(TransactionStatus.PROCESSING);
        }
    }

    /**
     * Actualiza la transacción con los datos del pago de MP (método existente mejorado)
     */
    private void updateTransactionFromPayment(TransactionEntity transaction, Payment payment) {
        // Actualizar estado
        updateTransactionStatusFromPayment(transaction, payment);

        // Actualizar información de tarjeta si existe
        if (payment.getCard() != null) {
            transaction.setMaskedCardNumber("**** **** **** " + payment.getCard().getLastFourDigits());
            transaction.setCardType(payment.getPaymentMethodId());
        }

        // Actualizar monto si es diferente
        if (payment.getTransactionAmount() != null &&
                !payment.getTransactionAmount().equals(transaction.getAmount())) {
            transaction.setAmount(payment.getTransactionAmount());
        }

        // Actualizar reference number si no está establecido
        if (transaction.getReferenceNumber() == null) {
            transaction.setReferenceNumber(payment.getId().toString());
        }

        transactionRepository.save(transaction);
        logger.info("Transacción actualizada - ID: {}, Estado: {}",
                transaction.getId(), transaction.getStatus());
    }

    /**
     * Marca una transacción como rechazada (método mejorado)
     */
    private void markTransactionAsRejected(String paymentId, String reason) {
        try {
            // Buscar por reference number
            Optional<TransactionEntity> transactionOpt = transactionRepository.findByReferenceNumber(paymentId);

            if (transactionOpt.isPresent()) {
                TransactionEntity transaction = transactionOpt.get();
                transaction.setStatus(TransactionStatus.REJECTED);
                transaction.setRejectionReason(reason);
                transaction.setProcessedAt(LocalDateTime.now());
                transactionRepository.save(transaction);

                logger.info("Transacción marcada como rechazada - Payment ID: {}, Razón: {}",
                        paymentId, reason);
            } else {
                logger.warn("No se encontró transacción para marcar como rechazada - Payment ID: {}",
                        paymentId);
            }
        } catch (Exception e) {
            logger.error("Error marcando transacción como rechazada - Payment ID: {}, Error: {}",
                    paymentId, e.getMessage());
        }
    }

    /**
     * Obtiene todos los métodos de pago disponibles
     */
    @Override
    public List<PaymentMethodDto> getAvailablePaymentMethods() {
        List<PaymentMethodEntity> methods = paymentMethodRepository.findByActiveTrue();
        return methods.stream()
                .map(method -> modelMapper.map(method, PaymentMethodDto.class))
                .collect(Collectors.toList());
    }

    /**
     * Busca transacciones por código de orden
     */
    @Override
    public List<TransactionResponse> getTransactionsByOrder(String orderCode) {
        List<TransactionEntity> transactions = transactionRepository.findByOrderCode(orderCode);
        return transactions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // Métodos privados auxiliares

    private TransactionEntity createPendingTransaction(PaymentRequest request, String preferenceId) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setOrderCode(request.getOrderCode());
        transaction.setPaymentMethodId(request.getPaymentMethodId());
        transaction.setAmount(request.getAmount());
        transaction.setDescription(request.getDescription());
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setReferenceNumber(preferenceId);
        transaction.setCreatedAt(LocalDateTime.now());

        return transactionRepository.save(transaction);
    }

    private TransactionEntity createNewTransaction(PaymentRequest request) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setOrderCode(request.getOrderCode());
        transaction.setPaymentMethodId(request.getPaymentMethodId());
        transaction.setAmount(request.getAmount());
        transaction.setDescription(request.getDescription());
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setCreatedAt(LocalDateTime.now());

        return transactionRepository.save(transaction);
    }

    private TransactionResponse processEffectivoPayment(TransactionEntity transaction, PaymentRequest request) {
        BigDecimal discountAmount = request.getAmount().multiply(new BigDecimal("0.15"));
        BigDecimal finalAmount = request.getAmount().subtract(discountAmount);

        transaction.setAmount(finalAmount);
        transaction.setStatus(TransactionStatus.APPROVED);
        transaction.setProcessedAt(LocalDateTime.now());
        transaction.setDescription(transaction.getDescription() + " - Descuento 15% efectivo aplicado");

        transactionRepository.save(transaction);
        return convertToResponse(transaction);
    }


    private TransactionResponse convertToResponse(TransactionEntity transaction) {
        TransactionResponse response = modelMapper.map(transaction, TransactionResponse.class);

        if (transaction.getPaymentMethodId() != null) {
            paymentMethodRepository.findById(transaction.getPaymentMethodId())
                    .ifPresent(method -> response.setPaymentMethodName(method.getName()));
        }

        return response;
    }
}