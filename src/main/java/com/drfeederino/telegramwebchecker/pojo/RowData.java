package com.drfeederino.telegramwebchecker.pojo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RowData {

    private String value;
    private String callbackData;

}
