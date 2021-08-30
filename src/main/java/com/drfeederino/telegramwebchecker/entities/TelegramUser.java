package com.drfeederino.telegramwebchecker.entities;

import lombok.Data;

import javax.persistence.*;
import java.util.List;

import static javax.persistence.CascadeType.ALL;

@Data
@Entity
@Table(name = "telegram_user")
public class TelegramUser {

    @Id
    Long id;
    @OneToMany(mappedBy = "telegramUser", cascade = ALL, targetEntity = TrackingCode.class)
    List<TrackingCode> trackCodes;
    UserStatus status;

}
