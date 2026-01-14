package br.com.protbike.input;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import jakarta.inject.Singleton;



@Singleton
public class InputHandler implements RequestHandler<String, String> {

    @Override
    public String handleRequest(String s, Context context) {
        return "";
    }
}