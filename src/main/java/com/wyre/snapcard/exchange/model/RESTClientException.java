package com.wyre.snapcard.exchange.model;

/**
 * Created by hshrivastava on 2/25/17.
 */
public class RESTClientException extends Exception {

    private static final long serialVersionUID = 1L;

    private int code;

    public RESTClientException(int code,String message){
        super(code+"_"+message);
        this.code=code;
    }

    public RESTClientException(Exception ex,String message,int code){
        super(code+"_"+message,ex);
        this.code=code;
    }

    public String getErrorCode(){
        return Integer.toString(code);
    }
}