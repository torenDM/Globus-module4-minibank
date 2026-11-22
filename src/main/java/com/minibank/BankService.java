package com.minibank;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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
public class BankService {

    private final Map<Long, Client> clients = new HashMap<>();
    private final Map<Long, Account> accounts = new HashMap<>();
    private final Map<Long, Transaction> transactions = new HashMap<>();

    private final AtomicLong clientIdSeq = new AtomicLong(1);
    private final AtomicLong accountIdSeq = new AtomicLong(1);
    private final AtomicLong transactionIdSeq = new AtomicLong(1);

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:postgresql://localhost:5433/minibank",
                "minibank",
                System.getenv("MINIBANK_DB_PASSWORD")
        );
    }

    public Client registerClient(String name, String email) {
        String sql = "INSERT INTO clients (name, email) VALUES (?, ?) RETURNING id";

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, name);
            statement.setString(2, email);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("База не вернула ID клиента");
                }
               long id = result.getLong("id");
                return new Client(id, name, email);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось сохранить клиента", e);
        }
    }

    public Account openAccount(Long clientId, String accountNumber) {
        getClient(clientId);

        String sql = """
            INSERT INTO accounts (client_id, account_number)
            VALUES (?, ?)
            RETURNING id, balance
            """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, clientId);
            statement.setString(2, accountNumber);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("База не вернула данные счёта");
                }

                Long id = result.getLong("id");
                BigDecimal balance = result.getBigDecimal("balance");
                return new Account(id,clientId,accountNumber,balance);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось открыть счёт", e);
        }
    }

    public Client getClient(Long clientId) {
        String sql = "SELECT id, name, email FROM clients WHERE id = ?";

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, clientId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new ClientNotFoundException(clientId);
                }

                long id = result.getLong("id");
                String name = result.getString("name");
                String email = result.getString("email");
                return new Client(id, name, email);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось прочитать клиента", e);
        }
    }

    public Account getAccount(Long accountId) {
        String sql = "SELECT id, client_id, account_number, balance FROM accounts WHERE id = ?";

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, accountId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new AccountNotFoundException(accountId);
                }

                long id = result.getLong("id");
                long client_id = result.getLong("client_id");
                String account_number = result.getString("account_number");
                BigDecimal balance = result.getBigDecimal("balance");
                return new Account(id, client_id, account_number, balance);
            }
        } catch (SQLException e) {
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
        String sql = "UPDATE accounts SET balance = balance + ? WHERE id = ?";

        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setBigDecimal(1, delta);
            statement.setLong(2, accountId);

            int result = statement.executeUpdate();

            if (result == 0) {
                throw new AccountNotFoundException(accountId);
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось записать изменить баланс для счета " + accountId, e);
        }
    }

    private void recordTransaction(Long accountId, BigDecimal amount, String type) {
        String sql = "INSERT INTO transactions (account_id, amount, type) VALUES (?, ?, ?)";

        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, accountId);
            statement.setBigDecimal(2, amount);
            statement.setString(3, type);

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Не удалось записать транзакцию для счёта " + accountId, e
            );
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
