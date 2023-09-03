package com.dws.challenge.repository;

import com.dws.challenge.domain.Account;
import com.dws.challenge.service.EmailNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class AccountsRepositoryInMemoryTest {

    @InjectMocks
    private AccountsRepositoryInMemory accountsRepository;

    @Mock
    private EmailNotificationService emailNotificationService;

    @Test
    void shouldTransferFundTestWhenBalanceIsSufficient() {
        Account debtorAccount = new Account("Id-123456", new BigDecimal("50000"));
        this.accountsRepository.createAccount(debtorAccount);

        Account creditorAccount = new Account("Id-78905", new BigDecimal("10000"));
        this.accountsRepository.createAccount(creditorAccount);
        accountsRepository.transferFund(debtorAccount.getAccountId(),
                creditorAccount.getAccountId(), new BigDecimal("25000"));

        doNothing().when(emailNotificationService).notifyAboutTransfer(any(), any());
        verify(emailNotificationService, times(2)).notifyAboutTransfer(any(), any());

    }

    @Test
    void shouldTransferFundTestWhenBalanceIsInSufficient() {
        Account debtorAccount = new Account("Id-123456", new BigDecimal("50000"));
        this.accountsRepository.createAccount(debtorAccount);

        Account creditorAccount = new Account("Id-78905", new BigDecimal("10000"));
        this.accountsRepository.createAccount(creditorAccount);

        assertThrows(
                RuntimeException.class,
                () -> accountsRepository.transferFund(debtorAccount.getAccountId(),
                        creditorAccount.getAccountId(), new BigDecimal(80000))
        );
    }
}