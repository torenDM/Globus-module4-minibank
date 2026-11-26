package com.minibank;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "transaction_date", nullable = false, insertable = false)
    private LocalDateTime timestamp;

    protected Transaction() { }

    public Transaction(Account account, BigDecimal amount, String type) {
        this.account = account;
        this.amount = amount;
        this.type = type;
    }

    public Long getId() { return id; }
    public Long getAccountId() { return account.getId(); }
    public BigDecimal getAmount() { return amount; }
    public String getType() { return type; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
