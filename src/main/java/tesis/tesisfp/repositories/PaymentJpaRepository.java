package tesis.tesisfp.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tesis.tesisfp.entities.PaymentMethodEntity;

import java.util.List;

@Repository
public interface PaymentJpaRepository extends JpaRepository<PaymentMethodEntity, String> {
    List<PaymentMethodEntity> findByActiveTrue();
}