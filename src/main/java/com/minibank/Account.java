package com.minibank;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "account_number", nullable = false, length = 34)
    private String accountNumber;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    protected Account() { }

    public Account(Client client, String accountNumber) {
        this.client = client;
        this.accountNumber = accountNumber;
        this.balance = BigDecimal.ZERO.setScale(4);
    }

    public Long getId() { return id; }
    public Long getClientId() { return client.getId(); }
    public String getAccountNumber() { return accountNumber; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
