package com.drfeederino.telegramwebchecker.entities;

import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "tracking_code")
public class TrackingCode {

    @Id
    @GeneratedValue
    Long id;
    @ManyToOne
    TelegramUser telegramUser;
    String code;
    String lastStatus;
    TrackingProvider provider;
    String email;

}
