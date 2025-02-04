package com.noslen.transaction_reader.service;

import com.noslen.transaction_reader.model.Transaction;

import java.util.List;
import java.util.Scanner;

public class CliService {
    public String promptForCategory(Transaction transaction, List<String> categoryList) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("\nSelect a category for the transaction:");
        System.out.println("Date: " + transaction.getPostDate() +
                                   " | Amount: " + formatAmount(transaction) +
                                   " | Description: " + transaction.getDescription());
        System.out.println("---------------------------------------------");

        // Display category list with numbers
        for (int i = 0; i < categoryList.size(); i++) {
            System.out.println((i + 1) + ". " + categoryList.get(i));
        }

        int choice = -1;
        while (choice < 1 || choice > categoryList.size()) {
            System.out.print("Enter the number corresponding to the category: ");
            while (!scanner.hasNextInt()) {
                System.out.println("Invalid input. Please enter a number between 1 and " + categoryList.size());
                scanner.next();
            }
            choice = scanner.nextInt();
        }

        return categoryList.get(choice - 1);
    }

    private String formatAmount(Transaction transaction) {
        double amount = transaction.getDebit() > 0 ? -transaction.getDebit() : transaction.getCredit();
        return String.format("$%.2f", amount);
    }
}
