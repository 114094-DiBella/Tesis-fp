package tesis.tesisfp.services;

import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import org.springframework.stereotype.Service;
import tesis.tesisfp.dtos.PaymentRequest;
import tesis.tesisfp.dtos.PaymentMethodDto;
import tesis.tesisfp.dtos.TransactionResponse;

import java.util.List;

@Service
public interface PaymentService {

    /**
     * Crea una preferencia de pago para usar con Checkout Bricks
     * @param request Datos del pago a procesar
     * @return ID de la preferencia creada
     */
    String createPaymentPreference(PaymentRequest request) throws MPException, MPApiException;

    /**
     * Procesa un pago recibido desde el frontend
     * @param request Datos del pago
     * @return Respuesta de la transacción
     */
    TransactionResponse processPayment(PaymentRequest request) throws MPException, MPApiException;

    /**
     * Obtiene el estado actual de un pago
     * @param paymentId ID del pago en Mercado Pago
     * @return Estado actualizado de la transacción
     */
    TransactionResponse getPaymentStatus(String paymentId) throws MPException, MPApiException;

    /**
     * Obtiene todos los métodos de pago disponibles
     * @return Lista de métodos de pago activos
     */
    List<PaymentMethodDto> getAvailablePaymentMethods();

    /**
     * Busca todas las transacciones de una orden específica
     * @param orderCode Código de la orden
     * @return Lista de transacciones
     */
    List<TransactionResponse> getTransactionsByOrder(String orderCode);
}