package com.uki.ebrgo4j;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.URISyntaxException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;
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
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

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

	public BigDecimal gasPrice() throws Exception {
		BigInteger gasPrice = ethereumRequest.ethGasPrice().getGasPrice();
		return Convert.fromWei(new BigDecimal(gasPrice), Convert.Unit.GWEI);
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

	public String ethTransfer(String password, String encryption, String to, double gasPrice, double value)
			throws Exception {

		WalletFile walletFile = objectMapper.readValue(encryption, WalletFile.class);
		Credentials credentials = Credentials.create(Wallet.decrypt(password, walletFile));

		RawTransaction rawTransaction = RawTransaction.createEtherTransaction(
				ethereumRequest.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING)
						.getTransactionCount(),
				Convert.toWei(BigDecimal.valueOf(gasPrice), Convert.Unit.GWEI).toBigInteger(),
				BigInteger.valueOf(21000), to,
				Convert.toWei(BigDecimal.valueOf(value), Convert.Unit.ETHER).toBigInteger());

		String hexValue = Numeric.toHexString(TransactionEncoder.signMessage(rawTransaction, credentials));
		return ethereumRequest.ethSendRawTransaction(hexValue).getTransactionHash();
	}

	public String erc20Transfer(String password, String encryption, String to, BigDecimal gasPrice, BigDecimal value,
			String contract, int decimals) throws Exception {
		WalletFile walletFile = objectMapper.readValue(encryption, WalletFile.class);
		Credentials credentials = Credentials.create(Wallet.decrypt(password, walletFile));

		Function function = new Function("transfer",
				Arrays.asList((Type<?>) new Address(to),
						(Type<?>) new Uint256(value.multiply(BigDecimal.TEN.pow(decimals)).toBigInteger())),
				Arrays.asList());
		String encodedFunction = FunctionEncoder.encode(function);

		org.web3j.protocol.core.methods.request.Transaction transaction = org.web3j.protocol.core.methods.request.Transaction
				.createFunctionCallTransaction(credentials.getAddress(), null,
						Convert.toWei(gasPrice, Convert.Unit.GWEI).toBigInteger(), null, contract, encodedFunction);
		BigInteger gasLimit = ethereumRequest.ethEstimateGas(transaction).getAmountUsed();

		RawTransaction rawTransaction = RawTransaction.createTransaction(
				ethereumRequest.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING)
						.getTransactionCount(),
				Convert.toWei(gasPrice, Convert.Unit.GWEI).toBigInteger(), gasLimit.multiply(BigInteger.valueOf(2)),
				contract, encodedFunction);

		String hexValue = Numeric.toHexString(TransactionEncoder.signMessage(rawTransaction, credentials));
		return ethereumRequest.ethSendRawTransaction(hexValue).getTransactionHash();
	}

	public String erc20TransferSign(String password, String encryption, String to, BigDecimal gasPrice, int gasLimit,
			BigDecimal value, String contract, int decimals) throws Exception {
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
