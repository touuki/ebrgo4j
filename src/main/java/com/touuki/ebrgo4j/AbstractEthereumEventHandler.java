package com.touuki.ebrgo4j;

import java.math.BigDecimal;
import java.util.Date;

import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

public abstract class AbstractEthereumEventHandler implements EthereumEventHandler {
	@Override
	public void ethReceived(Object addressId, Transaction transaction, Date timestamp) {
		BigDecimal amount = Convert.fromWei(new BigDecimal(transaction.getValue()), Convert.Unit.ETHER);
		ethReceived(addressId, transaction.getHash(), transaction.getFrom(), transaction.getTo(), amount, timestamp);
	};

	@Override
	public void erc20Received(Object addressId, Log log, Transaction transaction, Date timestamp, Erc20 erc20) {
		BigDecimal amount = new BigDecimal(Numeric.toBigInt(log.getData()));
		if (erc20.getDecimals() > 0) {
			amount = amount.divide(BigDecimal.TEN.pow(erc20.getDecimals()));
		}
		erc20Received(addressId, transaction.getHash(), "0x" + log.getTopics().get(1).substring(26),
				"0x" + log.getTopics().get(2).substring(26), amount, timestamp, erc20);
	};

	public void transactionFinished(Transaction transaction, TransactionReceipt receipt, Date timestamp) {
		boolean success = false;
		if (receipt.getStatus().equals("0x1")) {
			success = true;
		}
		BigDecimal fee = Convert.fromWei(new BigDecimal(transaction.getGasPrice().multiply(receipt.getGasUsed())),
				Convert.Unit.ETHER);
		transactionFinished(transaction.getHash(), success, transaction.getBlockNumber().longValue(), fee, timestamp);
	};

	abstract public void ethReceived(Object addressId, String transactionHash, String from, String to,
			BigDecimal amount, Date timestamp);

	abstract public void erc20Received(Object addressId, String transactionHash, String from, String to,
			BigDecimal amount, Date timestamp, Erc20 erc20);

	abstract public void transactionFinished(String transactionHash, boolean success, long blockNumber, BigDecimal fee,
			Date timestamp);
}
