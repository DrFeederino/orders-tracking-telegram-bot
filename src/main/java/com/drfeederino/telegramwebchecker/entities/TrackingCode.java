package com.drfeederino.telegramwebchecker.entities;

import com.drfeederino.telegramwebchecker.enums.TrackingProvider;
import lombok.Data;

import javax.persistence.*;
import javax.validation.constraints.Size;

@Data
@Entity
@Table(name = "tracking_code")
public class TrackingCode {

    String code;
    String email;
    @Id
    @GeneratedValue
    Long id;
    @Size(min = 10, max = 2048)
    String lastStatus;
    TrackingProvider provider;
    @ManyToOne
    TelegramUser telegramUser;

}
