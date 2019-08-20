package com.uki.ebrgo4j;

public class EthereumException extends RuntimeException{

	private static final long serialVersionUID = 7626129183821419713L;

	public EthereumException() {
        super();
    }

    public EthereumException(String message) {
        super(message);
    }


    public EthereumException(String message, Throwable cause) {
        super(message, cause);
    }

    public EthereumException(Throwable cause) {
        super(cause);
    }

}
