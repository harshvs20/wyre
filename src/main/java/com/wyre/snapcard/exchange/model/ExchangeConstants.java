package com.wyre.snapcard.exchange.model;

/**
 * Created by hshrivastava on 2/25/17.
 */
public enum ExchangeConstants {
    BID("bid"),
    ASK("ask"),
    LAST("last"),
    EXCHANGE("exchange"),
    DATE("date");

    private String name;

    ExchangeConstants(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
