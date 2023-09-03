package com.dws.challenge.domain;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;

/*
  Model class to store the transaction data
 */
@Data
public class TransactionDetails {
    @NotNull(message = "fromAccountId cannot be null in fund transfer request")
    private String fromAccountId;
    @NotNull(message = "toAccountId cannot be null in fund transfer request")
    private String toAccountId;
    @Positive(message = "transferAmount must always be positive")
    private BigDecimal transferAmount;
}
