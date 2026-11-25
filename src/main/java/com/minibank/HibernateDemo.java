package com.minibank;

import jakarta.persistence.Persistence;

import java.util.Map;

public class HibernateDemo {
    public static void main(String[] args) {
        try (BankService bank = new BankService()) {
            String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);

            Client client = bank.registerClient("Hibernate Demo", "jpa-" + suffix + "@example.test");
            Account account = bank.openAccount(client.getId(), "JPA-" + suffix);
            Account saved = bank.getAccount(account.getId());
            Account recipient = bank.openAccount(client.getId(), "JPA-TO-" + suffix);

            bank.changeBalance(account.getId(), new java.math.BigDecimal("100.00"));
            bank.transfer(account.getId(), recipient.getId(), new java.math.BigDecimal("25.00"));

            System.out.println("Отправитель: " + bank.getAccount(account.getId()).getBalance());
            System.out.println("Получатель: " + bank.getAccount(recipient.getId()).getBalance());
            System.out.printf("Клиент %d, счёт %s, баланс %s%n",
                    saved.getClientId(), saved.getAccountNumber(), saved.getBalance());
        }
    }
}