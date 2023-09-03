package com.dws.challenge.repository;

import com.dws.challenge.domain.Account;
import com.dws.challenge.exception.DuplicateAccountIdException;
import com.dws.challenge.service.EmailNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Repository
public class AccountsRepositoryInMemory implements AccountsRepository {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public void createAccount(Account account) throws DuplicateAccountIdException {
        Account previousAccount = accounts.putIfAbsent(account.getAccountId(), account);
        if (previousAccount != null) {
            throw new DuplicateAccountIdException(
                    "Account id " + account.getAccountId() + " already exists!");
        }
    }

    @Override
    public Account getAccount(String accountId) {
        return accounts.get(accountId);
    }

    @Override
    public void clearAccounts() {
        accounts.clear();
    }

    @Autowired
    private EmailNotificationService emailNotificationService;
    private final Lock transferFundLock = new ReentrantLock();

    @Override
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
            }

            creditorAccount = getAccount(creditorAccountId);
            if (creditorAccount != null) {
                creditorAccount.deposit(amountToTransfer);
            }
            if (emailNotificationService != null) {
                emailNotificationService.notifyAboutTransfer(debtorAccount, debtorAccount.getAccountId() + " debited for " + amountToTransfer);
                emailNotificationService.notifyAboutTransfer(creditorAccount, creditorAccount.getAccountId() + " credited with " + amountToTransfer);
            }
        } catch (Exception ex) {
            throw new RuntimeException("An exception occurred while transferring fund", ex);
        } finally {
            transferFundLock.unlock();
        }
    }

}
