package com.uki.ebrgo4j;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.web3j.protocol.core.methods.response.EthBlock.Block;
import org.web3j.protocol.core.methods.response.EthBlock.TransactionResult;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.exceptions.ContractCallException;

public class EthereumListener {
	// private static final org.slf4j.Logger log =
	// org.slf4j.LoggerFactory.getLogger(EthereumListener.class);

	private final EthereumService ethereumService;

	private final EthereumUtils ethereumUtils;

	private final ScheduledExecutorService executor;

	private boolean started = false;

	private long blockNumber = -1;

	private final Map<String, Erc20> erc20ForAddress = new ConcurrentHashMap<>();

	private final Map<String, Object> idForAddress = new ConcurrentHashMap<>();

	private final Map<String, Date> timeForTransactionHash = new ConcurrentHashMap<>();

	private int confirm = 9;

	public EthereumListener(EthereumUtils ethereumUtils, EthereumService ethereumService) throws Exception {
		this(ethereumUtils, ethereumService, Executors.newScheduledThreadPool(2));
	}

	public EthereumListener(EthereumUtils ethereumUtils, EthereumService ethereumService,
			ScheduledExecutorService executor) throws Exception {
		this.ethereumUtils = ethereumUtils;
		this.ethereumService = ethereumService;
		this.executor = executor;
	}

	public void addErc20(String contract) throws Exception {
		BigInteger totalSupply;
		try {
			totalSupply = ethereumUtils.erc20CallTotalSupply(contract);
		} catch (ContractCallException e) {
			if ("Empty value (0x) returned from contract".equals(e.getMessage())) {
				throw new ContractCallException("Not a valid erc20 address: " + contract);
			} else {
				throw e;
			}
		}
		String name = null;
		try {
			name = ethereumUtils.erc20CallName(contract);
		} catch (ContractCallException e) {
			if (!"Empty value (0x) returned from contract".equals(e.getMessage())) {
				throw e;
			}
		}
		String symbol = null;
		try {
			symbol = ethereumUtils.erc20CallSymbol(contract);
		} catch (ContractCallException e) {
			if (!"Empty value (0x) returned from contract".equals(e.getMessage())) {
				throw e;
			}
		}
		int decimals = 0;
		try {
			decimals = ethereumUtils.erc20CallDecimals(contract);
		} catch (ContractCallException e) {
			if (!"Empty value (0x) returned from contract".equals(e.getMessage())) {
				throw e;
			}
		}

		this.erc20ForAddress.put(contract, new Erc20(name, symbol, decimals,
				new BigDecimal(totalSupply).divide(BigDecimal.TEN.pow(decimals)), contract));
	}

	public void removeErc20(String contract) {
		this.erc20ForAddress.remove(contract);
	}

	public void addListeningAddress(String address, Object id) {
		this.idForAddress.put(address, id);
	}

	public void removeListeningAddress(String address) {
		this.idForAddress.remove(address);
	}
	
	public void addListeningTransaction(String transactionHash) {
		this.timeForTransactionHash.put(transactionHash, new Date());
	}

	public void removeListeningTransaction(String transactionHash) {
		this.timeForTransactionHash.remove(transactionHash);
	}

	public long getBlockNumber() {
		return blockNumber;
	}

	public void setBlockNumber(long blockNumber) {
		if (started) {
			throw new RuntimeException("The listener has already started");
		}
		this.blockNumber = blockNumber;
	}

	public int getConfirm() {
		return confirm;
	}

	public void setConfirm(int confirm) {
		if (started) {
			throw new RuntimeException("The listener has already started");
		}
		this.confirm = confirm;
	}

	public synchronized void start() throws Exception {
		if (started) {
			throw new RuntimeException("The listener has already started");
		} else {
			if (this.blockNumber <= 0) {
				this.blockNumber = ethereumUtils.ethGetNumber() - confirm + 1;
			}
			executor.scheduleWithFixedDelay(this::checkBlock, 0, 10, TimeUnit.SECONDS);
			started = true;
			// log.info("Ethereum listener started at block number {}", this.blockNumber);
		}
	}

	private synchronized void checkBlock() {
		try {
			for (long blockNumber = ethereumUtils.ethGetNumber(); blockNumber - confirm
					+ 1 >= this.blockNumber; this.blockNumber++) {
				// log.debug("Checking block {}", this.blockNumber);
				handleBlock(ethereumUtils.ethGetBlock(this.blockNumber));
				executor.execute(() -> ethereumService.blockCheckFinished(this.blockNumber));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void handleBlock(Block block) throws Exception {
		Date timestamp = new Date(block.getTimestamp().longValue() * 1000);
		for (TransactionResult<Transaction> transactionResult : block.getTransactions()) {
			handleTransaction(transactionResult.get(), timestamp);
		}
	}

	private void handleTransaction(Transaction transaction, Date timestamp) throws Exception {
		if (transaction.getTo() == null) {
			return;
		}

		if (erc20ForAddress.containsKey(transaction.getTo())) {
			TransactionReceipt receipt = ethereumUtils.ethGetTransactionReceipt(transaction.getHash());
			if (receipt.getStatus().equals("0x1")) {
				for (Log log : receipt.getLogs()) {
					if (log.getTopics().get(0)
							.equals("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")) {
						String address = "0x" + log.getTopics().get(2).substring(26);
						if (idForAddress.containsKey(address)) {
							Erc20 erc20 = erc20ForAddress.get(transaction.getTo());
							executor.execute(() -> ethereumService.erc20Received(idForAddress.get(address),
									log.getData(), transaction, timestamp, erc20));
						}
					}
				}
			}
		} else if (idForAddress.containsKey(transaction.getTo())) {
			TransactionReceipt receipt = ethereumUtils.ethGetTransactionReceipt(transaction.getHash());
			if (receipt.getStatus().equals("0x1")) {
				executor.execute(() -> ethereumService.ethReceived(idForAddress.get(transaction.getTo()), transaction,
						timestamp));
			}
		}

		if (timeForTransactionHash.containsKey(transaction.getHash())) {
			TransactionReceipt receipt = ethereumUtils.ethGetTransactionReceipt(transaction.getHash());
			executor.execute(() -> ethereumService.transactionFinished(transaction, receipt, timestamp));
			timeForTransactionHash.remove(transaction.getHash());
		}

	}

}
