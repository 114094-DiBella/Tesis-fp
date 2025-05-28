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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.stream.Collectors;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Value("${mercadopago.access.token}")
    private String accessToken;

    @Value("${app.base.url:http://localhost:4200}")
    private String baseUrl;

    @Autowired
    private PaymentJpaRepository paymentMethodRepository;

    @Autowired
    private TransactionJpaRepository transactionRepository;

    @Autowired
    private ModelMapper modelMapper;

    /**
     * Crea una preferencia de pago para Checkout Bricks
     */
    @Override
    @Transactional
    public String createPaymentPreference(PaymentRequest request) throws MPException, MPApiException {
        // Configurar token de acceso
        MercadoPagoConfig.setAccessToken(accessToken);

        // URLs de retorno
        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(baseUrl + "/payment/success")
                .pending(baseUrl + "/payment/pending")
                .failure(baseUrl + "/payment/failure")
                .build();

        // Item del pedido
        PreferenceItemRequest itemRequest = PreferenceItemRequest.builder()
                .id(request.getOrderCode())
                .title("Pedido #" + request.getOrderCode())
                .description(request.getDescription() != null ? request.getDescription() : "Compra en tienda")
                .quantity(1)
                .currencyId("ARS") // Peso argentino
                .unitPrice(request.getAmount())
                .build();

        List<PreferenceItemRequest> items = new ArrayList<>();
        items.add(itemRequest);

        // Crear preferencia
        PreferenceRequest preferenceRequest = PreferenceRequest.builder()
                .items(items)
                .backUrls(backUrls)
                .externalReference(request.getOrderCode())
                .statementDescriptor("TIENDA_ONLINE")
                .build();

        PreferenceClient client = new PreferenceClient();
        Preference preference = client.create(preferenceRequest);

        // Crear transacción pendiente
        createPendingTransaction(request, preference.getId());

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
        MercadoPagoConfig.setAccessToken(accessToken);

        PaymentClient client = new PaymentClient();
        Payment payment = client.get(Long.valueOf(paymentId));

        // Buscar transacción en base de datos
        TransactionEntity transaction = transactionRepository
                .findByReferenceNumber(paymentId)
                .orElseThrow(() -> new RuntimeException("Transacción no encontrada"));

        // Actualizar estado según respuesta de MP
        updateTransactionFromPayment(transaction, payment);

        return convertToResponse(transaction);
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
        // Para efectivo, aplicar descuento del 15%
        BigDecimal discountAmount = request.getAmount().multiply(new BigDecimal("0.15"));
        BigDecimal finalAmount = request.getAmount().subtract(discountAmount);

        transaction.setAmount(finalAmount);
        transaction.setStatus(TransactionStatus.APPROVED);
        transaction.setProcessedAt(LocalDateTime.now());
        transaction.setDescription(transaction.getDescription() + " - Descuento 15% efectivo aplicado");

        transactionRepository.save(transaction);
        return convertToResponse(transaction);
    }

    private void updateTransactionFromPayment(TransactionEntity transaction, Payment payment) {
        switch (payment.getStatus()) {
            case "approved":
                transaction.setStatus(TransactionStatus.APPROVED);
                break;
            case "pending":
                transaction.setStatus(TransactionStatus.PENDING);
                break;
            case "rejected":
                transaction.setStatus(TransactionStatus.REJECTED);
                transaction.setRejectionReason(payment.getStatusDetail());
                break;
            case "cancelled":
                transaction.setStatus(TransactionStatus.CANCELLED);
                break;
            default:
                transaction.setStatus(TransactionStatus.PROCESSING);
        }

        if (payment.getCard() != null) {
            transaction.setMaskedCardNumber(payment.getCard().getLastFourDigits());
            transaction.setCardType(payment.getPaymentMethodId());
        }

        transaction.setProcessedAt(LocalDateTime.now());
        transactionRepository.save(transaction);
    }

    private void markTransactionAsRejected(String orderCode, String reason) {
        transactionRepository.findFirstByOrderCodeOrderByCreatedAtDesc(orderCode)
                .ifPresent(transaction -> {
                    transaction.setStatus(TransactionStatus.REJECTED);
                    transaction.setRejectionReason(reason);
                    transaction.setProcessedAt(LocalDateTime.now());
                    transactionRepository.save(transaction);
                });
    }

    private TransactionResponse convertToResponse(TransactionEntity transaction) {
        TransactionResponse response = modelMapper.map(transaction, TransactionResponse.class);

        // Obtener nombre del método de pago
        if (transaction.getPaymentMethodId() != null) {
            paymentMethodRepository.findById(transaction.getPaymentMethodId())
                    .ifPresent(method -> response.setPaymentMethodName(method.getName()));
        }

        return response;
    }
}