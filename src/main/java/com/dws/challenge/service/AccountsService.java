package com.dws.challenge.service;

import com.dws.challenge.domain.Account;
import com.dws.challenge.repository.AccountsRepository;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AccountsService {
  @Autowired
  private EmailNotificationService emailNotificationService;
  private final Lock transferFundLock = new ReentrantLock();

  //private final Map<String, Lock> accountLocks = new HashMap<>();

  @Getter
  private final AccountsRepository accountsRepository;

  @Autowired
  public AccountsService(AccountsRepository accountsRepository) {
    this.accountsRepository = accountsRepository;
  }

  public void createAccount(Account account) {
    this.accountsRepository.createAccount(account);
  }

  public Account getAccount(String accountId) {
    return this.accountsRepository.getAccount(accountId);
  }

  /*public void transferFund(String debtorAccountId, String creditorAccountId, BigDecimal amountToTransfer) {

    Lock debtorLock = accountLocks.computeIfAbsent(debtorAccountId, (accountId) -> new ReentrantLock());
    Lock creditorLock = accountLocks.computeIfAbsent(creditorAccountId, (accountId) -> new ReentrantLock());

    // Always acquire locks in a consistent order to prevent deadlock
    Lock firstLock = debtorAccountId.compareTo(creditorAccountId) < 0 ? debtorLock : creditorLock;
    Lock secondLock = debtorAccountId.compareTo(creditorAccountId) < 0 ? creditorLock : debtorLock;

    if (debtorAccountId.equals(creditorAccountId)) {
      throw new IllegalArgumentException("Cannot transfer funds to the same account.");
    }

    firstLock.lock();
    try {
      secondLock.lock();
      try {
        Account debtorAccount = accountsRepository.getAccount(debtorAccountId);
        Account creditorAccount = accountsRepository.getAccount(creditorAccountId);

        if (debtorAccount == null || creditorAccount == null) {
          throw new RuntimeException("Account not found.");
        }

        if (debtorAccount.getBalance().compareTo(amountToTransfer) < 0) {
          throw new RuntimeException("Not enough balance in debtor account for transfer.");
        }

        debtorAccount.withdraw(amountToTransfer);
        creditorAccount.deposit(amountToTransfer);

        if (emailNotificationService != null) {
          emailNotificationService.notifyAboutTransfer(debtorAccount, creditorAccountId + " has been credited with " + amountToTransfer);
          emailNotificationService.notifyAboutTransfer(creditorAccount, debtorAccountId + " has been debited with " + amountToTransfer);
        }
      } finally {
        secondLock.unlock();
      }
    } finally {
      firstLock.unlock();
    }
  }*/

  public void transferFund(String debtorAccountId, String creditorAccountId, BigDecimal amountToTransfer) {
    transferFundLock.lock();
    Account debtorAccount = null;
    Account creditorAccount = null;
    try {
      debtorAccount = getAccount(debtorAccountId);
      if (debtorAccount != null) {
        if (debtorAccount.getBalance().compareTo(amountToTransfer) >= 0) {
          debtorAccount.withdraw(amountToTransfer);
        } else {
          throw new RuntimeException("Not enough balance in account for transfer");
        }
      } else {
        throw new RuntimeException("debtorAccount cannot be NULL incase of fund transfer");
      }

      creditorAccount = getAccount(creditorAccountId);
      if (creditorAccount != null) {
        creditorAccount.deposit(amountToTransfer);
      } else {
        throw new RuntimeException("creditorAccount cannot be NULL incase of fund transfer");
      }
      if (emailNotificationService != null) {
        emailNotificationService.notifyAboutTransfer(creditorAccount, debtorAccount.getAccountId() + " has been debited with " + amountToTransfer);
        emailNotificationService.notifyAboutTransfer(debtorAccount, creditorAccount.getAccountId() + " has been credited with " + amountToTransfer);
      }
    } catch (Exception ex) {
      throw new RuntimeException("An exception occurred while transferring fund", ex);
    } finally {
      transferFundLock.unlock();
    }
  }

}
