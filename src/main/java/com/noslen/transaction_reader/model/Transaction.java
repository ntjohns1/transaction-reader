package com.noslen.transaction_reader.model;

import java.time.LocalDate;

public class Transaction {
    private final LocalDate postDate;
    private final String description;
    private final double debit;
    private final double credit;
    private final double balance;
    private String classification;

    public Transaction(LocalDate postDate, String description, double debit, double credit, double balance) {
        this.postDate = postDate;
        this.description = description;
        this.debit = debit;
        this.credit = credit;
        this.balance = balance;
        this.classification = null; // Initially null, set later
    }

    public LocalDate getPostDate() {
        return postDate;
    }

    public String getDescription() {
        return description;
    }

    public double getDebit() {
        return debit;
    }

    public double getCredit() {
        return credit;
    }

    public double getBalance() {
        return balance;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }
}