package com.uki.ebrgo4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.web3j.protocol.core.Response;

public class EthereumInvocationHandler implements InvocationHandler{
	
	private final EthereumRequestImpl target;

	public EthereumInvocationHandler(EthereumRequestImpl target) {
		this.target = target;
	}
	
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		if (Response.class.isAssignableFrom(method.getReturnType())) {
			try {
				Response result = (Response) method.invoke(target, args);
				if (result.hasError()) {
					throw new EthereumException(result.getError().getMessage());
				} else {
					return result;
				}
			} catch (WebsocketNotConnectedException e) {
				target.webSocketReconnect();
				return method.invoke(proxy, args);
			}
		} else {
			return method.invoke(target, args);
		}
	}

}
