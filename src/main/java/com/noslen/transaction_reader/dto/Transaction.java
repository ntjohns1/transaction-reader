package com.noslen.transaction_reader.dto;

import java.time.LocalDate;

public record Transaction(LocalDate postDate, String description, double debit) {
    // Optionally, include validation logic in the constructor:

    //    public Transaction {
    //        if (debit < 0) {
    //            throw new IllegalArgumentException("Debit cannot be negative");
    //        }
    //    }
}
