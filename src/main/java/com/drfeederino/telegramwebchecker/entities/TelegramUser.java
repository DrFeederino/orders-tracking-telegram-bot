package com.drfeederino.telegramwebchecker.entities;

import com.drfeederino.telegramwebchecker.enums.UserStatus;
import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.util.List;

import static javax.persistence.CascadeType.ALL;

@Data
@Entity
@Table(name = "telegram_user")
public class TelegramUser {

    @Id
    Long id;
    UserStatus status;
    @OneToMany(mappedBy = "telegramUser", cascade = ALL, targetEntity = TrackingCode.class)
    List<TrackingCode> trackCodes;

}
