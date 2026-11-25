package com.minibank;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceException;

/**
 * MiniBank — "MVP", который наспех собрал предыдущий разработчик:
 * вся логика в одном классе, все данные — в HashMap, персистентности
 * нет вообще (перезапуск процесса — и всё пропало).
 *
 * ПРОБЛЕМА (найдёшь и исправишь, когда дойдёт очередь — модуль 7.1,
 * когда появится @Transactional): в transfer() проверка существования
 * счёта-получателя происходит ПОСЛЕ списания денег со счёта-
 * отправителя. Если счёт-получатель не существует, деньги уже спишутся
 * с первого счёта и "исчезнут" — их некуда вернуть, потому что метод
 * прерывается исключением до зачисления.
 *
 * Эта проблема не решается на HashMap "в лоб" без искусственных
 * костылей (можно вручную откатывать баланс в catch, но это не
 * настоящее решение) — она по-настоящему решается только когда
 * появится транзакционность (сначала в БД — ACID, модуль 4, а затем
 * в коде — @Transactional, модуль 7.1). До этого момента баг просто
 * существует и это нормально: то, что появится в модуле 7.1, — как
 * раз тот инструмент, который проблему полностью закрывает.
 */


public class BankService implements AutoCloseable {

    private final Map<Long, Client> clients = new HashMap<>();
    private final Map<Long, Account> accounts = new HashMap<>();
    private final Map<Long, Transaction> transactions = new HashMap<>();

    private final AtomicLong clientIdSeq = new AtomicLong(1);
    private final AtomicLong accountIdSeq = new AtomicLong(1);
    private final AtomicLong transactionIdSeq = new AtomicLong(1);

    private final EntityManagerFactory factory;

    public BankService() {
        String password = System.getenv("MINIBANK_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Укажи MINIBANK_DB_PASSWORD в настройках запуска");
        }

        factory = Persistence.createEntityManagerFactory(
                "minibank",
                Map.of("jakarta.persistence.jdbc.password", password)
        );
    }

    @Override
    public void close() {
        factory.close();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:postgresql://localhost:5433/minibank",
                "minibank",
                System.getenv("MINIBANK_DB_PASSWORD")
        );
    }

    public Client registerClient(String name, String email) {
        try {
            return factory.callInTransaction(em -> {
                Client client = new Client(name, email);
                em.persist(client);
                return client;
            });
        } catch (PersistenceException e) {
            throw new IllegalStateException("Не удалось сохранить клиента", e);
        }
    }

    public Account openAccount(Long clientId, String accountNumber) {
        try {
            return factory.callInTransaction(em -> {
                Client client = em.find(Client.class, clientId);
                if (client == null) {
                    throw new ClientNotFoundException(clientId);
                }

                Account account = new Account(client, accountNumber);
                em.persist(account);
                return account;
            });
        } catch (PersistenceException e) {
            throw new IllegalStateException("Не удалось открыть счёт", e);
        }
    }

    public Client getClient(Long clientId) {
        try (var em = factory.createEntityManager()) {
            Client client = em.find(Client.class, clientId);
            if (client == null) {
                throw new ClientNotFoundException(clientId);
            }
            return client;
        } catch (PersistenceException e) {
            throw new IllegalStateException("Не удалось прочитать клиента", e);
        }
    }

    public Account getAccount(Long accountId) {
        try (var em = factory.createEntityManager()) {
            Account account = em.find(Account.class, accountId);
            if (account == null) {
                throw new AccountNotFoundException(accountId);
            }
            return account;
        } catch (PersistenceException e) {
            throw new IllegalStateException("Не удалось прочитать аккаунт", e);
        }
    }

    public void transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        Account from = getAccount(fromAccountId);

        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(fromAccountId);
        }

        changeBalance(fromAccountId, amount.negate());
        recordTransaction(fromAccountId, amount.negate(), "TRANSFER_OUT");

        changeBalance(toAccountId, amount);
        recordTransaction(toAccountId, amount, "TRANSFER_IN");
    }

    public void changeBalance(Long accountId, BigDecimal delta) {
        try {
            factory.runInTransaction(em -> {
                int updated = em.createQuery("""
                    UPDATE Account a
                    SET a.balance = a.balance + :delta
                    WHERE a.id = :accountId
                    """)
                        .setParameter("delta", delta)
                        .setParameter("accountId", accountId)
                        .executeUpdate();

                if (updated == 0) {
                    throw new AccountNotFoundException(accountId);
                }
            });
        } catch (PersistenceException e) {
            throw new IllegalStateException(
                    "Не удалось изменить баланс счёта " + accountId, e);
        }
    }

    private void recordTransaction(Long accountId, BigDecimal amount, String type) {
        try {
            factory.runInTransaction(em -> {
                Account account = em.getReference(Account.class, accountId);
                em.persist(new Transaction(account, amount, type));
            });
        } catch (PersistenceException e) {
            throw new IllegalStateException(
                    "Не удалось записать транзакцию для счёта " + accountId, e);
        }
    }

    static class AccountNotFoundException extends IllegalArgumentException {
        AccountNotFoundException(long accountId) {
            super("Account not found: " + accountId);
        }
    }

    static class InsufficientFundsException extends IllegalStateException {
        InsufficientFundsException(long accountId) {
            super("Insufficient funds on account " + accountId);
        }
    }

    static class ClientNotFoundException extends IllegalArgumentException {
        ClientNotFoundException(long clientId) {
            super("Client not found: " + clientId);
        }
    }
}
