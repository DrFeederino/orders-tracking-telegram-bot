package com.drfeederino.telegramwebchecker.repository;

import com.drfeederino.telegramwebchecker.entities.TrackingCode;
import com.drfeederino.telegramwebchecker.entities.TrackingProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrackingCodeRepository extends JpaRepository<TrackingCode, Long> {

    Optional<TrackingCode> findTopByTelegramUserIdAndEmailNull(Long id);

    TrackingCode findTopByTelegramUserIdAndProviderNull(Long id);

    List<TrackingCode> findAllByTelegramUserId(Long id);

    List<TrackingCode> findAllByProvider(TrackingProvider trackingProvider);

    @Transactional
    void deleteAllByTelegramUserId(Long id);

    @Transactional
    void deleteById(Long id);

}
