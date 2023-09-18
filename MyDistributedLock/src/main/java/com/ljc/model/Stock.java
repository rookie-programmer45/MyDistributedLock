package com.ljc.model;

import lombok.Data;

@Data
public class Stock {
    private Integer id;

    private String goodId = "1001";

    private Integer count = 5000;

    private Integer version;

    public void resetCount() {
        count = 5000;
    }
}
