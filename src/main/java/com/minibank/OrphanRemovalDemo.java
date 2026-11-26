package com.minibank;

import jakarta.persistence.Persistence;

import java.util.Map;
import java.util.UUID;

public class OrphanRemovalDemo {
    public static void main(String[] args) {
        String password = System.getenv("MINIBANK_DB_PASSWORD");
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        try (var factory = Persistence.createEntityManagerFactory(
                "minibank", Map.of("jakarta.persistence.jdbc.password", password))) {

            Account created = factory.callInTransaction(em -> {
                Client client = new Client("Orphan Demo", "orphan-" + suffix + "@example.test");
                Account account = new Account(client, "ORPHAN-" + suffix);
                client.getAccounts().add(account);
                em.persist(client);
                return account;
            });

            factory.runInTransaction(em -> {
                Client client = em.find(Client.class, created.getClientId());
                boolean removed = client.getAccounts()
                        .removeIf(account -> account.getId().equals(created.getId()));

                if (!removed) {
                    throw new IllegalStateException("Тестовый счёт отсутствует в коллекции");
                }
            });

            boolean exists = factory.callInTransaction(
                    em -> em.find(Account.class, created.getId()) != null);

            if (exists) {
                throw new IllegalStateException("Счёт остался в базе");
            }

            System.out.println("Счёт " + created.getId() + " удалён из PostgreSQL");
        }
    }
}