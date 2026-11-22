package com.minibank;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.Map;

public class WebMain {
    private static final ObjectMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public static void main(String[] args) throws IOException {
        BankService bank = new BankService();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8080), 0);
        server.createContext("/transfer", exchange -> handleTransfer(exchange, bank));
        server.createContext("/clients", exchange -> handleCreateClient(exchange, bank));
        server.createContext("/accounts", exchange -> handleCreateAccount(exchange, bank));
        server.start();
        System.out.println("MiniBank запущен на http://127.0.0.1:8080");
    }

    private static void handleTransfer(HttpExchange exchange, BankService bank)
            throws IOException {
        try (exchange) {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                sendJson(exchange, 405, Map.of("error", "Используй POST /transfer"));
                return;
            }

            TransferRequest request;
            try {
                request = JSON.readValue(exchange.getRequestBody(), TransferRequest.class);
            } catch (JsonProcessingException e) {
                sendJson(exchange, 400, Map.of("error", "Некорректный JSON"));
                return;
            }

            if (request == null || request.fromAccountId() == null
                    || request.toAccountId() == null || request.amount() == null) {
                sendJson(exchange, 400, Map.of("error", "Укажи оба счёта и сумму"));
                return;
            }

            if (request.amount().signum() <= 0) {
                sendJson(exchange, 400, Map.of("error", "Сумма должна быть больше нуля"));
                return;
            }

            try {
                bank.transfer(request.fromAccountId(), request.toAccountId(), request.amount());
                sendJson(exchange, 200, Map.of("message", "Перевод выполнен"));
            } catch (BankService.AccountNotFoundException e) {
                sendJson(exchange, 404, Map.of("error", e.getMessage()));
            } catch (BankService.InsufficientFundsException e) {
                sendJson(exchange, 409, Map.of("error", e.getMessage()));
            } catch (IllegalStateException e) {
                sendJson(exchange, 500, Map.of("error", "Не удалось выполнить перевод"));
            }
        }
    }

    private static void handleCreateClient(HttpExchange exchange, BankService bank)
            throws IOException {
        try (exchange) {
            if (!requirePost(exchange)) {
                return;
            }

            CreateClientRequest request;
            try {
                request = JSON.readValue(exchange.getRequestBody(), CreateClientRequest.class);
            } catch (JsonProcessingException e) {
                sendJson(exchange, 400, Map.of("error", "Некорректный JSON"));
                return;
            }

            if (request == null || request.name() == null || request.name().isBlank()
                    || request.email() == null || request.email().isBlank()) {
                sendJson(exchange, 400, Map.of("error", "Укажи имя и email"));
                return;
            }

            try {
                Client client = bank.registerClient(request.name(), request.email());
                sendJson(exchange, 201, client);
            } catch (IllegalStateException e) {
                sendJson(exchange, 500, Map.of("error", "Не удалось создать клиента"));
            }
        }
    }

    private static void handleCreateAccount(HttpExchange exchange, BankService bank)
            throws IOException {
        try (exchange) {
            if (!requirePost(exchange)) {
                return;
            }

            CreateAccountRequest request;
            try {
                request = JSON.readValue(exchange.getRequestBody(), CreateAccountRequest.class);
            } catch (JsonProcessingException e) {
                sendJson(exchange, 400, Map.of("error", "Некорректный JSON"));
                return;
            }

            if (request == null || request.clientId() == null || request.clientId() <= 0
                    || request.accountNumber() == null || request.accountNumber().isBlank()) {
                sendJson(exchange, 400, Map.of("error", "Укажи ID клиента и номер счёта"));
                return;
            }

            try {
                Account account = bank.openAccount(request.clientId(), request.accountNumber());
                sendJson(exchange, 201, account);
            } catch (BankService.ClientNotFoundException e) {
                sendJson(exchange, 404, Map.of("error", e.getMessage()));
            } catch (IllegalStateException e) {
                sendJson(exchange, 500, Map.of("error", "Не удалось открыть счёт"));
            }
        }
    }

    private static boolean requirePost(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            return true;
        }
        exchange.getResponseHeaders().set("Allow", "POST");
        sendJson(exchange, 405, Map.of("error", "Используй POST"));
        return false;
    }

    private static void sendJson(HttpExchange exchange, int status, Object body)
            throws IOException {
        byte[] response = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, response.length);

        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    private record TransferRequest(Long fromAccountId, Long toAccountId, BigDecimal amount) {
    }

    private record CreateClientRequest(String name, String email) {
    }

    private record CreateAccountRequest(Long clientId, String accountNumber) {
    }
}