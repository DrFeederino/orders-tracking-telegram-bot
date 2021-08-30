package com.drfeederino.telegramwebchecker.repository;


import com.drfeederino.telegramwebchecker.entities.TelegramUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
public interface TelegramUserRepository extends JpaRepository<TelegramUser, Long> {

    Optional<TelegramUser> findById(Long id);

    @Transactional
    void deleteById(Long id);

}
