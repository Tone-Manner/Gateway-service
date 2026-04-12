package com.textrefiner.gatewayservice.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@Order(-1) // 스프링의 기본 에러 핸들러보다 우선순위를 높여서 우리가 먼저 가로챔
@RequiredArgsConstructor
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        // 이미 프론트엔드에게 응답이 출발했다면 처리 불가
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        // 기본 에러 세팅
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorMessage = "게이트웨이 내부 서버 오류가 발생했습니다.";

        // 스프링 라우팅 관련 에러(404, 503 등)인지 확인
        if (ex instanceof ResponseStatusException responseStatusException) {
            status = (HttpStatus) responseStatusException.getStatusCode();
            errorMessage = responseStatusException.getReason();
        }

        // 1. 응답 헤더 설정 (SON으로)
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 2. 프론트엔드가 파싱하기 편한 JSON 규격 맵핑
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", status.value());
        errorResponse.put("message", errorMessage != null ? errorMessage : ex.getMessage());
        errorResponse.put("path", exchange.getRequest().getURI().getPath());

        // 3. Map을 JSON 문자열(Byte)로 변환해서 응답에 쏘기
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("JSON 변환 중 에러 발생", e);
            return Mono.error(e);
        }
    }
}