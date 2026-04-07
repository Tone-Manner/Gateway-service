package com.textrefiner.gatewayservice.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;

@Slf4j
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private final SecretKey secretKey;

    // application.yml에서 시크릿 키를 불러와서 암호화 키로 변환
    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        super(Config.class);
        byte[] keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public static class Config {
        // 필터에 필요한 설정값이 있다면 여기에 추가 (지금은 비워둬도 됨)
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // 1. Authorization 헤더가 있는지 확인
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, "No Authorization header", HttpStatus.UNAUTHORIZED);
            }

            // 2. 헤더에서 토큰 문자열 추출 (Bearer ... )
            String authorizationHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);
            String token = authorizationHeader.replace("Bearer ", "");

            // 3. 토큰 검증
            try {
                Jwts.parserBuilder()
                        .setSigningKey(secretKey)
                        .build()
                        .parseClaimsJws(token);
                // (선택 사항) 토큰에서 userId 등을 꺼내서 헤더에 새로 넣어주면 뒤쪽 서비스들이 편함
            } catch (Exception e) {
                log.error("JWT Validation Error: {}", e.getMessage());
                return onError(exchange, "JWT token is not valid", HttpStatus.UNAUTHORIZED);
            }

            // 검증 성공. 다음 필터나 목적지로 통과
            return chain.filter(exchange);
        };
    }

    // 에러 발생 시 401 응답을 내려주는 헬퍼 메서드
    private Mono<Void> onError(org.springframework.web.server.ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        log.error(err);
        return response.setComplete();
    }
}