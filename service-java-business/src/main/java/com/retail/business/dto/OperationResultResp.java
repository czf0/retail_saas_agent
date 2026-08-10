package com.retail.business.dto;

import lombok.Data;

@Data
public class OperationResultResp {
    private Boolean success = true;
    private String message;
}
