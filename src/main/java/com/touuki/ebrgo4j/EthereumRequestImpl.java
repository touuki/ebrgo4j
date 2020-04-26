package com.touuki.ebrgo4j;

import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.admin.Admin;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.ipc.UnixIpcService;
import org.web3j.protocol.ipc.WindowsIpcService;
import org.web3j.protocol.websocket.WebSocketClient;
import org.web3j.protocol.websocket.WebSocketService;

public class EthereumRequestImpl implements EthereumRequest {
	private Web3j web3j;
	private WebSocketClient webSocketClient;

	public EthereumRequestImpl(String url) throws ConnectException, URISyntaxException {
		setWeb3jService(url);
	}

	public synchronized void webSocketReconnect() throws ConnectException {
		if (webSocketClient != null && !webSocketClient.isOpen()) {
			webSocketClient = new WebSocketClient(webSocketClient.getURI());
			WebSocketService webSocketService = new WebSocketService(webSocketClient, false);
			webSocketService.connect();
			web3j.shutdown();
			web3j = Admin.build(webSocketService);
		}
	}

	public Web3j getWeb3j() {
		return web3j;
	}

	public void setWeb3jService(String url) throws ConnectException, URISyntaxException {
		if (url.startsWith("http://") || url.startsWith("https://")) {
			web3j = Admin.build(new HttpService(url));
		} else if (url.startsWith("ws://")) {
			webSocketClient = new WebSocketClient(new URI(url));
			WebSocketService webSocketService = new WebSocketService(webSocketClient, false);
			webSocketService.connect();
			web3j = Admin.build(webSocketService);
		} else {
			String os = System.getProperty("os.name");
			if (os.toLowerCase().startsWith("win")) {
				web3j = Admin.build(new WindowsIpcService(url));
			} else {
				web3j = Admin.build(new UnixIpcService(url));
			}
		}
	}

	@Override
	public EthGasPrice ethGasPrice() throws InterruptedException, ExecutionException {
		return web3j.ethGasPrice().sendAsync().get();
	}

	@Override
	public EthGetBalance ethGetBalance(String address) throws InterruptedException, ExecutionException {
		return web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).sendAsync().get();
	}

	@Override
	public EthSendTransaction ethSendRawTransaction(String signedTransactionData)
			throws InterruptedException, ExecutionException {
		return web3j.ethSendRawTransaction(signedTransactionData).sendAsync().get();
	}

	@Override
	public EthBlockNumber ethBlockNumber() throws InterruptedException, ExecutionException {
		return web3j.ethBlockNumber().sendAsync().get();
	}

	@Override
	public EthEstimateGas ethEstimateGas(Transaction transaction) throws InterruptedException, ExecutionException {
		return web3j.ethEstimateGas(transaction).sendAsync().get();
	}

	@Override
	public EthGetTransactionCount ethGetTransactionCount(String address, DefaultBlockParameter defaultBlockParameter)
			throws InterruptedException, ExecutionException {
		return web3j.ethGetTransactionCount(address, defaultBlockParameter).sendAsync().get();
	}

	@Override
	public EthBlock ethGetBlockByNumber(DefaultBlockParameter defaultBlockParameter,
			boolean returnFullTransactionObjects) throws InterruptedException, ExecutionException {
		return web3j.ethGetBlockByNumber(defaultBlockParameter, returnFullTransactionObjects).sendAsync().get();
	}

	@Override
	public EthGetTransactionReceipt ethGetTransactionReceipt(String transactionHash)
			throws InterruptedException, ExecutionException {
		return web3j.ethGetTransactionReceipt(transactionHash).sendAsync().get();
	}

	@Override
	public EthCall ethCall(org.web3j.protocol.core.methods.request.Transaction transaction,
			DefaultBlockParameter defaultBlockParameter) throws InterruptedException, ExecutionException {
		return web3j.ethCall(transaction, defaultBlockParameter).sendAsync().get();
	}

}
