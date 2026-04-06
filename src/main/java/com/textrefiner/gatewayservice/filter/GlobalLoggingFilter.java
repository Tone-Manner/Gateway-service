package com.textrefiner.gatewayservice.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class GlobalLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 요청(Request) 들어올 때
        log.info("[Global Filter Start] Request ID: {}, Path: {}",
                exchange.getRequest().getId(),
                exchange.getRequest().getURI().getPath());

        long startTime = System.currentTimeMillis();

        // 2. 다음 필터나 목적지(Auth Service 등)로 이동
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            // 3. 응답(Response) 나갈 때
            long endTime = System.currentTimeMillis();
            log.info("[Global Filter End] Request ID: {}, Status: {}, Time Taken: {}ms",
                    exchange.getRequest().getId(),
                    exchange.getResponse().getStatusCode(),
                    (endTime - startTime));
        }));
    }

    // 필터의 실행 순서를 정합니다. 숫자가 작을수록 가장 먼저 실행.
    @Override
    public int getOrder() {
        return -1; // 최우선순위로 설정
    }
}