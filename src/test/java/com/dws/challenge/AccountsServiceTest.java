package com.dws.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.dws.challenge.domain.Account;
import com.dws.challenge.exception.DuplicateAccountIdException;
import com.dws.challenge.repository.AccountsRepository;
import com.dws.challenge.service.AccountsService;
import com.dws.challenge.service.EmailNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class AccountsServiceTest {

  private AccountsService accountsService;

  @Mock
  private EmailNotificationService emailNotificationService;

  @Mock
  private AccountsRepository accountsRepository;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    accountsService = new AccountsService(accountsRepository);
  }

  @Test
  void addAccount() {
    Account account = new Account("Id-123");
    account.setBalance(new BigDecimal(1000));
    // Mock the behavior of the accountsRepository to return the account when createAccount is called
    when(accountsRepository.getAccount("Id-123")).thenReturn(null);
    // Call the method to add the account
    accountsService.createAccount(account);

    // Verify that the accountRepository's createAccount method was called with the correct account
    verify(accountsRepository, times(1)).createAccount(account);
  }

  @Test
  void addAccount_failsOnDuplicateId() {
    String uniqueId = "Id-" + System.currentTimeMillis();
    Account account = new Account(uniqueId);
    // Mock the behavior of the accountsRepository to throw exception when duplicate account exists
    doThrow(new DuplicateAccountIdException("Account id " + uniqueId + " already exists!"))
            .when(accountsRepository).createAccount(account);

    assertThrows(DuplicateAccountIdException.class, () -> {
      accountsService.createAccount(account);
    });
    // Verify that accountsService.createAccount was called with the correct account object
    verify(accountsRepository, times(1)).createAccount(account);
  }

  /*
    junit to cover the fund transfer incase of sufficient balance in account
  */
  @Test
  void shouldTransferFundWhenBalanceIsSufficientTest() {
    Account debtorAccount = new Account("Id-123456", new BigDecimal("50000.00"));
    when(accountsRepository.getAccount(debtorAccount.getAccountId())).thenReturn(debtorAccount);


    Account creditorAccount = new Account("Id-78905", new BigDecimal("10000.00"));
    when(accountsRepository.getAccount(creditorAccount.getAccountId())).thenReturn(creditorAccount);

    doNothing().when(emailNotificationService).notifyAboutTransfer(any(), any());
    BigDecimal transferAmount = new BigDecimal("25000.00");


    accountsService.transferFund(debtorAccount.getAccountId(),
            creditorAccount.getAccountId(), transferAmount);

    assertEquals(new BigDecimal("25000.00"), debtorAccount.getBalance());
    assertEquals(new BigDecimal("35000.00"), creditorAccount.getBalance());

    emailNotificationService.notifyAboutTransfer(debtorAccount,creditorAccount+" has been credited with "+transferAmount);
    emailNotificationService.notifyAboutTransfer(creditorAccount,debtorAccount+" has been debited with "+transferAmount);

    verify(emailNotificationService, times(2)).notifyAboutTransfer(any(), any());

  }

  /*
    junit to cover the fund transfer incase of insufficient balance in account
  */
  @Test
  void shouldTransferFundWhenBalanceIsInSufficientTest() {
    Account debtorAccount = new Account("Id-123456", new BigDecimal("50000.0"));
    when(accountsRepository.getAccount(debtorAccount.getAccountId())).thenReturn(debtorAccount);

    Account creditorAccount = new Account("Id-78905", new BigDecimal("10000.0"));
    when(accountsRepository.getAccount(creditorAccount.getAccountId())).thenReturn(creditorAccount);

    BigDecimal transferAmount = new BigDecimal(80000.0);

    assertThrows(
            RuntimeException.class,
            () -> accountsService.transferFund(debtorAccount.getAccountId(),
                    creditorAccount.getAccountId(), transferAmount)
    );
  }

  /*
  junit to throw exception incase one of the accounts is null
   */
  @Test
  void shouldThrowExceptionWhenOneOfTheAccountIsNullTest()
  {
    Account debtorAccount = new Account(null);
    when(accountsRepository.getAccount(debtorAccount.getAccountId())).thenReturn(debtorAccount);

    Account creditorAccount = new Account("Id-123456", new BigDecimal("10000.0"));
    when(accountsRepository.getAccount(creditorAccount.getAccountId())).thenReturn(creditorAccount);

    BigDecimal transferAmount = new BigDecimal(5000.0);

    assertThrows(
            RuntimeException.class,
            () -> accountsService.transferFund(debtorAccount.getAccountId(),
                    creditorAccount.getAccountId(), transferAmount)
    );
  }

  /*
   junit to cover the fund transfer incase of concurrency
  */
  @Test
  void shouldTransferFundConcurrentlyTest() throws InterruptedException {
    // Create two accounts with initial balances
    Account account1 = new Account("Id-1");
    account1.setBalance(new BigDecimal(1000.0));
    BigDecimal initialBalanceInAccount1 = account1.getBalance();
    when(accountsRepository.getAccount(account1.getAccountId())).thenReturn(account1);

    Account account2 = new Account("Id-2");
    account2.setBalance(new BigDecimal(1000.0));
    BigDecimal initialBalanceInAccount2 = account2.getBalance();
    when(accountsRepository.getAccount(account2.getAccountId())).thenReturn(account2);

    doNothing().when(emailNotificationService).notifyAboutTransfer(any(), any());

    // Create an ExecutorService to simulate concurrent transfers
    ExecutorService executorService = Executors.newFixedThreadPool(2);

    // Define the number of concurrent transfers
    int concurrentTransfers = 5;

    // Define the amount to transfer in each concurrent transfer
    BigDecimal transferAmount = new BigDecimal(10.0);

    // Submit concurrent transfer tasks
    for (int i = 0; i < concurrentTransfers; i++) {
      executorService.submit(() -> accountsService.transferFund("Id-1", "Id-2", transferAmount));
      emailNotificationService.notifyAboutTransfer(account1,account2.getAccountId()+" has been credited with "+transferAmount);
      emailNotificationService.notifyAboutTransfer(account2,account1.getAccountId()+" has been debited with "+transferAmount);
    }

    // Shutdown the executor and wait for all tasks to complete
    executorService.shutdown();
    executorService.awaitTermination(10, TimeUnit.SECONDS);

    // Check the final balances after concurrent transfers
    Account finalAccount1 = accountsService.getAccount("Id-1");
    Account finalAccount2 = accountsService.getAccount("Id-2");

    // The final balance of account1 should be the initial balance minus the total transferred amount
    BigDecimal expectedBalance1 = initialBalanceInAccount1.subtract(transferAmount.multiply(BigDecimal.valueOf(concurrentTransfers)));
    assertThat(finalAccount1.getBalance()).isEqualByComparingTo(expectedBalance1);

    // The final balance of account2 should be the initial balance plus the total transferred amount
    BigDecimal expectedBalance2 = initialBalanceInAccount2.add(transferAmount.multiply(BigDecimal.valueOf(concurrentTransfers)));
    assertThat(finalAccount2.getBalance()).isEqualByComparingTo(expectedBalance2);

    verify(emailNotificationService, times(10)).notifyAboutTransfer(any(), any());
  }

  /*
   junit to cover the fund transfer incase of concurrent transfer b/w two different pair of accounts
  */
  @RepeatedTest(10) // Repeat the test multiple times to check for concurrency issues
  @DisplayName("Concurrent Transfer Test for different pair of accounts")
  public void testConcurrentTransfer() throws InterruptedException {
    // Set up two accounts with initial balances
    Account account1 = new Account("Id-1", new BigDecimal(1000));
    Account account2 = new Account("Id-2", new BigDecimal(1000));
    Account account3 = new Account("Id-3", new BigDecimal(1000));
    Account account4 = new Account("Id-4", new BigDecimal(1000));

    when(accountsRepository.getAccount(account1.getAccountId())).thenReturn(account1);
    when(accountsRepository.getAccount(account2.getAccountId())).thenReturn(account2);
    when(accountsRepository.getAccount(account3.getAccountId())).thenReturn(account3);
    when(accountsRepository.getAccount(account4.getAccountId())).thenReturn(account4);

    // Set up the transfer amounts
    BigDecimal amountToTransfer1 = new BigDecimal(100);
    BigDecimal amountToTransfer2 = new BigDecimal(200);

    // Create a CountDownLatch to synchronize the start of concurrent transfers
    CountDownLatch startLatch = new CountDownLatch(2);

    // Create an ExecutorService to run concurrent transfers
    ExecutorService executorService = Executors.newFixedThreadPool(2);

    // Start the first transfer
    executorService.submit(() -> {
      startLatch.countDown();
      try {
        startLatch.await(); // Wait for both transfers to start concurrently
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
      accountsService.transferFund(account1.getAccountId(), account2.getAccountId(), amountToTransfer1);
    });

    // Start the second transfer
    executorService.submit(() -> {
      startLatch.countDown();
      try {
        startLatch.await(); // Wait for both transfers to start concurrently
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
      accountsService.transferFund(account3.getAccountId(), account4.getAccountId(), amountToTransfer2);
    });

    // Shutdown the executor service and wait for both transfers to complete
    executorService.shutdown();
    executorService.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

    // Check the final balances after transfers
    Account debtorAccount1 = accountsRepository.getAccount(account1.getAccountId());
    Account creditorAccount1 = accountsRepository.getAccount(account2.getAccountId());
    Account debtorAccount2 = accountsRepository.getAccount(account3.getAccountId());
    Account creditorAccount2 = accountsRepository.getAccount(account4.getAccountId());

    // Perform assertions to check if the transfers were successful
    assertEquals(new BigDecimal(900), debtorAccount1.getBalance());
    assertEquals(new BigDecimal(1100), creditorAccount1.getBalance());
    assertEquals(new BigDecimal(800), debtorAccount2.getBalance());
    assertEquals(new BigDecimal(1200), creditorAccount2.getBalance());
  }
}
