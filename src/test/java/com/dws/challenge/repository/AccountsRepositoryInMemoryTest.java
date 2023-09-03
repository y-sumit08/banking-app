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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class AccountsRepositoryInMemoryTest {

    @InjectMocks
    private AccountsRepositoryInMemory accountsRepository;

    @Mock
    private EmailNotificationService emailNotificationService;

    /*
      junit to cover the fund transfer incase of sufficient balance in account
    */
    @Test
    void shouldTransferFundWhenBalanceIsSufficientTest() {
        Account debtorAccount = new Account("Id-123456", new BigDecimal("50000"));
        this.accountsRepository.createAccount(debtorAccount);

        Account creditorAccount = new Account("Id-78905", new BigDecimal("10000"));
        this.accountsRepository.createAccount(creditorAccount);
        accountsRepository.transferFund(debtorAccount.getAccountId(),
                creditorAccount.getAccountId(), new BigDecimal("25000"));

        doNothing().when(emailNotificationService).notifyAboutTransfer(any(), any());
        verify(emailNotificationService, times(2)).notifyAboutTransfer(any(), any());

    }

    /*
      junit to cover the fund transfer incase of insufficient balance in account
    */
    @Test
    void shouldTransferFundWhenBalanceIsInSufficientTest() {
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

    /*
     junit to cover the fund transfer incase of concurrency
    */
    @Test
    void shouldTransferFundConcurrentlyTest() throws InterruptedException {
        // Create two accounts with initial balances
        Account account1 = new Account("Id-1");
        account1.setBalance(new BigDecimal(1000));
        BigDecimal initialBalanceInAccount1 = account1.getBalance();
        accountsRepository.createAccount(account1);

        Account account2 = new Account("Id-2");
        account2.setBalance(new BigDecimal(1000));
        BigDecimal initialBalanceInAccount2 = account2.getBalance();
        accountsRepository.createAccount(account2);

        // Create an ExecutorService to simulate concurrent transfers
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        // Define the number of concurrent transfers
        int concurrentTransfers = 100;

        // Define the amount to transfer in each concurrent transfer
        BigDecimal transferAmount = new BigDecimal(10);

        // Submit concurrent transfer tasks
        for (int i = 0; i < concurrentTransfers; i++) {
            executorService.submit(() -> accountsRepository.transferFund("Id-1", "Id-2", transferAmount));
        }

        // Shutdown the executor and wait for all tasks to complete
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // Check the final balances after concurrent transfers
        Account finalAccount1 = accountsRepository.getAccount("Id-1");
        Account finalAccount2 = accountsRepository.getAccount("Id-2");

        // The final balance of account1 should be the initial balance minus the total transferred amount
        BigDecimal expectedBalance1 = initialBalanceInAccount1.subtract(transferAmount.multiply(BigDecimal.valueOf(concurrentTransfers)));
        assertThat(finalAccount1.getBalance()).isEqualByComparingTo(expectedBalance1);

        // The final balance of account2 should be the initial balance plus the total transferred amount
        BigDecimal expectedBalance2 = initialBalanceInAccount2.add(transferAmount.multiply(BigDecimal.valueOf(concurrentTransfers)));
        assertThat(finalAccount2.getBalance()).isEqualByComparingTo(expectedBalance2);

        doNothing().when(emailNotificationService).notifyAboutTransfer(any(), any());
        verify(emailNotificationService, times(200)).notifyAboutTransfer(any(), any());
    }
}