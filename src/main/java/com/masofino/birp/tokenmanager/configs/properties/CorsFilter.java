package com.masofino.birp.tokenmanager.configs.properties;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class CorsFilter extends Filter {
    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        // add the three standard CORS response headers
        Map<String, List<String>> headers = exchange.getResponseHeaders();
        headers.put("Access-Control-Allow-Origin", List.of("*"));
        headers.put("Access-Control-Allow-Methods", List.of("GET","POST","OPTIONS"));
        headers.put("Access-Control-Allow-Headers", List.of("Content-Type","Authorization"));

        // if this is a preflight, reply 204 No Content immediately
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        // otherwise continue on to your normal handler
        chain.doFilter(exchange);
    }

    @Override
    public String description() {
        return "Adds CORS support to every request";
    }
}