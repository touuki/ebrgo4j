package com.touuki.ebrgo4j;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.URISyntaxException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.Wallet;
import org.web3j.crypto.WalletFile;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import org.web3j.tx.exceptions.ContractCallException;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;
import org.web3j.utils.Convert.Unit;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EthereumUtils {
	private static final ObjectMapper objectMapper = new ObjectMapper();

	static {
		objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	private EthereumRequest ethereumRequest;
	private BigInteger gasPrice;
	private long gasPriceTimestamp = 0;
	private long gasPriceUpdatePeriod = 180_000L;

	public EthereumUtils(String url) throws ConnectException, URISyntaxException {
		EthereumRequestImpl ethereumRequestImpl = new EthereumRequestImpl(url);
		ethereumRequest = (EthereumRequest) Proxy.newProxyInstance(ethereumRequestImpl.getClass().getClassLoader(),
				ethereumRequestImpl.getClass().getInterfaces(), new EthereumInvocationHandler(ethereumRequestImpl));
	}

	public List<String> createAccount(String password) throws InvalidAlgorithmParameterException,
			JsonProcessingException, NoSuchAlgorithmException, NoSuchProviderException, CipherException {
		ECKeyPair ecKeyPair = Keys.createEcKeyPair();
		String address = Numeric.prependHexPrefix(Keys.getAddress(ecKeyPair));
		WalletFile walletFile = Wallet.createStandard(password, ecKeyPair);
		String encryption = objectMapper.writeValueAsString(walletFile);
		return Arrays.asList(address, encryption);
	}

	public String changePassword(String password, String encryption, String newPassword)
			throws JsonParseException, JsonMappingException, IOException, CipherException {
		WalletFile walletFile = objectMapper.readValue(encryption, WalletFile.class);
		Credentials credentials = Credentials.create(Wallet.decrypt(password, walletFile));
		walletFile = Wallet.createStandard(newPassword, credentials.getEcKeyPair());
		return objectMapper.writeValueAsString(walletFile);
	}

	public BigInteger ethGasPrice() throws Exception {
		if (new Date().getTime() > gasPriceTimestamp + gasPriceUpdatePeriod) {
			updateGasPrice();
		}
		return gasPrice;
	}

	public BigDecimal gasPrice() throws Exception {
		return Convert.fromWei(new BigDecimal(ethGasPrice()), Convert.Unit.GWEI);
	}

	public synchronized void updateGasPrice() throws Exception {
		long now = new Date().getTime();
		if (now > gasPriceTimestamp + gasPriceUpdatePeriod) {
			gasPrice = ethereumRequest.ethGasPrice().getGasPrice();
			gasPriceTimestamp = now;
		}
	}

	public Block ethGetBlock(long blockNumber) throws Exception {
		return ethereumRequest.ethGetBlockByNumber(DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)), true)
				.getBlock();
	}

	public String sendSigned(String hexValue) throws Exception {
		return ethereumRequest.ethSendRawTransaction(hexValue).getTransactionHash();
	}

	public long ethGetNumber() throws Exception {
		return ethereumRequest.ethBlockNumber().getBlockNumber().longValue();
	}

	public TransactionReceipt ethGetTransactionReceipt(String transactionHash) throws Exception {
		return ethereumRequest.ethGetTransactionReceipt(transactionHash).getTransactionReceipt().get();
	}

	public List<Erc20Transfer> getErc20Transfer(String transactionHash) throws Exception {
		TransactionReceipt receipt = ethGetTransactionReceipt(transactionHash);
		if (receipt == null) {
			return Collections.emptyList();
		}
		int decimals = -1;
		List<Erc20Transfer> erc20Transfers = new ArrayList<Erc20Transfer>();
		for (Log log : receipt.getLogs()) {
			if (log.getTopics().get(0).equals("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")) {
				Erc20Transfer erc20Transfer = new Erc20Transfer();
				erc20Transfer.setHash(transactionHash);
				erc20Transfer.setFrom("0x" + log.getTopics().get(1).substring(26));
				erc20Transfer.setTo("0x" + log.getTopics().get(2).substring(26));
				erc20Transfer.setContract(receipt.getTo());
				erc20Transfer.setStatus(receipt.getStatus().equals("0x1"));
				if (decimals == -1) {
					decimals = erc20CallDecimals(receipt.getTo());
				}
				erc20Transfer.setAmount(
						new BigDecimal(Numeric.toBigInt(log.getData())).divide(BigDecimal.TEN.pow(decimals)));
				erc20Transfers.add(erc20Transfer);
			}
		}
		return erc20Transfers;
	}

	public BigDecimal getEthAmount(String address) throws Exception {
		return Convert.fromWei(new BigDecimal(ethereumRequest.ethGetBalance(address).getBalance()), Unit.ETHER);
	}

	public BigDecimal getErc20Amount(String address, String contract) throws Exception {
		int decimals = erc20CallDecimals(contract);
		BigInteger rawValue = erc20CallBalanceOf(contract, address);
		return new BigDecimal(rawValue).divide(BigDecimal.TEN.pow(decimals));
	}

	public String ethTransfer(String password, String encryption, String to, BigDecimal value) throws Exception {
		WalletFile walletFile = objectMapper.readValue(encryption, WalletFile.class);
		return ethTransfer(Credentials.create(Wallet.decrypt(password, walletFile)), to, ethGasPrice(), value);
	}

	public String ethTransfer(String password, String encryption, String to, BigDecimal gasPrice, BigDecimal value)
			throws Exception {
		WalletFile walletFile = objectMapper.readValue(encryption, WalletFile.class);
		return ethTransfer(Credentials.create(Wallet.decrypt(password, walletFile)), to,
				Convert.toWei(gasPrice, Convert.Unit.GWEI).toBigInteger(), value);
	}

	public String ethTransfer(String privateKey, String to, BigDecimal value) throws Exception {
		return ethTransfer(Credentials.create(privateKey), to, ethGasPrice(), value);
	}

	public String ethTransfer(String privateKey, String to, BigDecimal gasPrice, BigDecimal value) throws Exception {
		return ethTransfer(Credentials.create(privateKey), to,
				Convert.toWei(gasPrice, Convert.Unit.GWEI).toBigInteger(), value);
	}

	protected String ethTransfer(Credentials credentials, String to, BigInteger gasPrice, BigDecimal value)
			throws Exception {

		RawTransaction rawTransaction = RawTransaction.createEtherTransaction(
				ethereumRequest.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING)
						.getTransactionCount(),
				gasPrice, BigInteger.valueOf(21000), to, Convert.toWei(value, Convert.Unit.ETHER).toBigInteger());

		String hexValue = Numeric.toHexString(TransactionEncoder.signMessage(rawTransaction, credentials));
		return ethereumRequest.ethSendRawTransaction(hexValue).getTransactionHash();
	}

	public String erc20Transfer(String password, String encryption, String to, BigDecimal value, String contract)
			throws Exception {
		WalletFile walletFile = objectMapper.readValue(encryption, WalletFile.class);
		return erc20Transfer(Credentials.create(Wallet.decrypt(password, walletFile)), to, ethGasPrice(), value,
				contract);
	}

	public String erc20Transfer(String password, String encryption, String to, BigDecimal gasPrice, BigDecimal value,
			String contract) throws Exception {
		WalletFile walletFile = objectMapper.readValue(encryption, WalletFile.class);
		return erc20Transfer(Credentials.create(Wallet.decrypt(password, walletFile)), to,
				Convert.toWei(gasPrice, Convert.Unit.GWEI).toBigInteger(), value, contract);
	}

	public String erc20Transfer(String privateKey, String to, BigDecimal value, String contract) throws Exception {
		return erc20Transfer(Credentials.create(privateKey), to, ethGasPrice(), value, contract);
	}

	public String erc20Transfer(String privateKey, String to, BigDecimal gasPrice, BigDecimal value, String contract)
			throws Exception {
		return erc20Transfer(Credentials.create(privateKey), to,
				Convert.toWei(gasPrice, Convert.Unit.GWEI).toBigInteger(), value, contract);
	}

	protected String erc20Transfer(Credentials credentials, String to, BigInteger gasPrice, BigDecimal value,
			String contract) throws Exception {
		int decimals = erc20CallDecimals(contract);
		Function function = new Function("transfer",
				Arrays.asList((Type<?>) new Address(to),
						(Type<?>) new Uint256(value.multiply(BigDecimal.TEN.pow(decimals)).toBigInteger())),
				Arrays.asList());
		String encodedFunction = FunctionEncoder.encode(function);

		org.web3j.protocol.core.methods.request.Transaction transaction = org.web3j.protocol.core.methods.request.Transaction
				.createFunctionCallTransaction(credentials.getAddress(), null, gasPrice, null, contract,
						encodedFunction);
		BigInteger gasLimit = ethereumRequest.ethEstimateGas(transaction).getAmountUsed();

		RawTransaction rawTransaction = RawTransaction.createTransaction(
				ethereumRequest.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING)
						.getTransactionCount(),
				gasPrice, gasLimit.multiply(BigInteger.valueOf(2)), contract, encodedFunction);

		String hexValue = Numeric.toHexString(TransactionEncoder.signMessage(rawTransaction, credentials));
		return ethereumRequest.ethSendRawTransaction(hexValue).getTransactionHash();
	}

	public String erc20TransferSign(String password, String encryption, String to, BigDecimal gasPrice, int gasLimit,
			BigDecimal value, String contract) throws Exception {
		int decimals = erc20CallDecimals(contract);
		WalletFile walletFile = objectMapper.readValue(encryption, WalletFile.class);
		Credentials credentials = Credentials.create(Wallet.decrypt(password, walletFile));

		Function function = new Function("transfer",
				Arrays.asList((Type<?>) new Address(to),
						(Type<?>) new Uint256(value.multiply(BigDecimal.TEN.pow(decimals)).toBigInteger())),
				Arrays.asList());
		String encodedFunction = FunctionEncoder.encode(function);

		RawTransaction rawTransaction = RawTransaction.createTransaction(
				ethereumRequest.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING)
						.getTransactionCount(),
				Convert.toWei(gasPrice, Convert.Unit.GWEI).toBigInteger(), BigInteger.valueOf(gasLimit), contract,
				encodedFunction);

		return Numeric.toHexString(TransactionEncoder.signMessage(rawTransaction, credentials));
	}

	public int erc20CallDecimals(String contract) throws Exception {
		Function function = new Function("decimals", Arrays.asList(), Arrays.asList(new TypeReference<Uint8>() {
		}));
		return ethCallSingleValueReturn(contract, function, BigInteger.class).intValue();
	}

	public String erc20CallName(String contract) throws Exception {
		Function function = new Function("name", Arrays.asList(), Arrays.asList(new TypeReference<Utf8String>() {
		}));
		return ethCallSingleValueReturn(contract, function, String.class);
	}

	public String erc20CallSymbol(String contract) throws Exception {
		Function function = new Function("symbol", Arrays.asList(), Arrays.asList(new TypeReference<Utf8String>() {
		}));
		return ethCallSingleValueReturn(contract, function, String.class);
	}

	public BigInteger erc20CallTotalSupply(String contract) throws Exception {
		Function function = new Function("totalSupply", Arrays.asList(), Arrays.asList(new TypeReference<Uint256>() {
		}));
		return ethCallSingleValueReturn(contract, function, BigInteger.class);
	}

	public BigInteger erc20CallBalanceOf(String contract, String address) throws Exception {
		Function function = new Function("balanceOf", Arrays.asList(new Address(address)),
				Arrays.asList(new TypeReference<Uint256>() {
				}));
		return ethCallSingleValueReturn(contract, function, BigInteger.class);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private <T> T ethCallSingleValueReturn(String contract, Function function, Class<T> returnType) throws Exception {
		String encodedFunction = FunctionEncoder.encode(function);
		org.web3j.protocol.core.methods.request.Transaction transaction = org.web3j.protocol.core.methods.request.Transaction
				.createFunctionCallTransaction(Address.DEFAULT.toString(), null, null, null, contract, encodedFunction);
		List<Type> values = FunctionReturnDecoder.decode(
				ethereumRequest.ethCall(transaction, DefaultBlockParameterName.LATEST).getValue(),
				function.getOutputParameters());
		if (!values.isEmpty()) {
			Type result = values.get(0);

			Object value = result.getValue();
			if (returnType.isAssignableFrom(value.getClass())) {
				return (T) value;
			} else if (result.getClass().equals(Address.class) && returnType.equals(String.class)) {
				return (T) result.toString(); // cast isn't necessary
			} else {
				throw new ContractCallException(
						"Unable to convert response: " + value + " to expected type: " + returnType.getSimpleName());
			}
		} else {
			throw new ContractCallException("Empty value (0x) returned from contract");
		}
	}
}
