package com.minibank;

import jakarta.persistence.Persistence;

import java.util.List;
import java.util.Map;

public class FetchDemo {
    public static void main(String[] args) {
        String password = System.getenv("MINIBANK_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Укажи MINIBANK_DB_PASSWORD в настройках запуска");
        }

        try (var factory = Persistence.createEntityManagerFactory(
                "minibank", Map.of("jakarta.persistence.jdbc.password", password));
             var em = factory.createEntityManager()) {

            System.out.println("--- Начало замера ---");

            List<Client> clients = em.createQuery("""
                SELECT c FROM Client c
                LEFT JOIN FETCH c.accounts
                WHERE c.id IN :ids
                ORDER BY c.id
                """, Client.class)
                    .setParameter("ids", List.of(4L, 5L, 6L))
                    .getResultList();

            for (Client client : clients) {
                System.out.printf("Клиент %d — счетов %d%n",
                        client.getId(), client.getAccounts().size());
            }

            System.out.println("--- Конец замера ---");
        }
    }
}