# ebrgo4j

## Usage
先`maven install`该项目至本地Maven仓库，在自己的项目`pom.xml`中引入依赖
```xml
<dependency>
	<groupId>com.touuki</groupId>
	<artifactId>ebrgo4j</artifactId>
	<version>0.1.1</version>
</dependency>
```

### With Spring
#### Utilities
创建一个Bean添加到Spring容器中
```java
@Bean
public EthereumUtils ethereumUtils() throws ConnectException, URISyntaxException {
	String url = "https://mainnet.infura.io/your-api-token"; //ETH节点地址
	return new EthereumUtils(url);
}
```
在需要的类中注入
```java
@Autowired
private EthereumUtils ethereumUtils;
```
然后就可以使用其与以太坊进行交互，比如发起ETH转账并获得其交易hash
```java
//以3GWei的手续费率向0xc6c81a3cA70D8A15084b05d2f4b3AC4E2456D846地址转1.1eth
String hash = ethereumUtils.ethTransfer("your-password", "your-encryption", 
	"0xc6c81a3cA70D8A15084b05d2f4b3AC4E2456D846", BigDecimal.valueOf(3), BigDecimal.valueOf(1.1));
```
生成新的账户
```java
List<String> keyPair = ethereumUtils.createAccount("your-password");
String address = keyPair.get(0);
String encryption = keyPair.get(1);
```
#### Listener
创建一个监听事件处理器
```java
@Component
public class EthereumEventHandlerImpl extends AbstractEthereumEventHandler{

	@Override
	public void blockCheckFinished(long blockNumber) {
		// 该块检查完了会触发该事件，可以持久化blockNumber以供重启时用，防止漏块
		
	}

	@Override
	public void ethReceived(Object addressId, String from, String to, BigDecimal amount, Date timestamp) {
		// 收到ETH时会触发该事件
		
	}

	@Override
	public void erc20Received(Object addressId, String from, String to, BigDecimal amount, Date timestamp,
			Erc20 erc20) {
		// 收到ERC20时会触发该事件
		
	}

	@Override
	public void transactionFinished(String transactionHash, boolean success, long blockNumber, BigDecimal fee,
			Date timestamp) {
		// 监听的Transaction完成时会触发该事件
		
	}

}

```

创建一个Bean添加到Spring容器中
```java
@Bean
public EthereumListener ethereumListener(EthereumUtils ethereumUtils, EthereumEventHandler ethereumEventHandler) throws Exception {
	EthereumListener ethereumListener = new EthereumListener(ethereumUtils, ethereumEventHandler);
	ethereumListener.addErc20("0xdac17f958d2ee523a2206206994597c13d831ec7"); //添加要监听的ERC20币种，传人合约地址，比如USDT
	//ethereumListener.addListeningAddress("0xc6c81a3cA70D8A15084b05d2f4b3AC4E2456D846", 1); //添加监听的地址及其ID（转入时会回传ID）
	ethereumListener.setBlockNumber(-1); //开始扫描的块数，默认从最新块开始，但这样每次重启时会漏块，所以要自行处理块数的持久化
	//ethereumListener.setConfirm(9); //可选设置，设置多少个块确认以后返回事件
	return ethereumListener;
}
```
在需要的类中注入
```java
@Autowired
private EthereumListener ethereumListener;
```
开始监听
```java
ethereumListener.start()
```

可以将需要监听的交易hash交给Listener，比如上面eth的转账
```java
ethereumListener.addListeningTransaction(hash);
```
也可以添加监听的地址，比如通过Utilities创建的地址
```java
ethereumListener.addListeningAddress(address, "your-custom-id");
```
