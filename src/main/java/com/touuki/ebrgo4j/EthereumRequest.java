package com.touuki.ebrgo4j;

import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;

public interface EthereumRequest {
	EthGasPrice ethGasPrice() throws Exception;

	EthSendTransaction ethSendRawTransaction(String signedTransactionData) throws Exception;

	EthBlockNumber ethBlockNumber() throws Exception;

	EthEstimateGas ethEstimateGas(Transaction transaction) throws Exception;

	EthGetTransactionCount ethGetTransactionCount(String address, DefaultBlockParameter defaultBlockParameter)
			throws Exception;

	EthBlock ethGetBlockByNumber(DefaultBlockParameter defaultBlockParameter, boolean returnFullTransactionObjects)
			throws Exception;
	
	EthGetTransactionReceipt ethGetTransactionReceipt(String transactionHash) throws Exception;
	
	EthCall ethCall(org.web3j.protocol.core.methods.request.Transaction transaction,
            DefaultBlockParameter defaultBlockParameter) throws Exception;
}
