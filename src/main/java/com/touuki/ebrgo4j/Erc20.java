package com.touuki.ebrgo4j;

import java.math.BigDecimal;

public class Erc20 {
	private final String name;
	private final String symbol;
	private final int decimals;
	private final BigDecimal totalSupply;
	private final String address;
	
	public Erc20(String name, String symbol, int decimals, BigDecimal totalSupply, String address) {
		this.name = name;
		this.symbol = symbol;
		this.decimals = decimals;
		this.totalSupply = totalSupply;
		this.address = address;
	}

	public String getName() {
		return name;
	}

	public String getSymbol() {
		return symbol;
	}

	public int getDecimals() {
		return decimals;
	}

	public BigDecimal getTotalSupply() {
		return totalSupply;
	}

	public String getAddress() {
		return address;
	}
}
