package com.touuki.ebrgo4j;

import java.util.Date;

import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

public interface EthereumEventHandler {
	void blockCheckFinished(long blockNumber);

	void ethReceived(Object addressId, Transaction transaction, Date timestamp);

	void erc20Received(Object addressId, Log log, Transaction transaction, Date timestamp, Erc20 erc20);

	void transactionFinished(Transaction transaction, TransactionReceipt receipt, Date timestamp);
}
