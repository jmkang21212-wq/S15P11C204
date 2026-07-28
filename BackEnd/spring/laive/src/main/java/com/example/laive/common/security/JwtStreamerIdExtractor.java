package com.example.laive.common.security;

import com.example.laive.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * JWT subject에 저장된 streamerId 해석을 Controller마다 반복하지 않게 한다.
 */
@Component
public class JwtStreamerIdExtractor {

    public long extract(Jwt jwt) {
        try {
            long streamerId = Long.parseLong(jwt.getSubject());
            if (streamerId <= 0) {
                throw invalidSubject();
            }
            return streamerId;
        } catch (NumberFormatException | NullPointerException exception) {
            throw invalidSubject();
        }
    }

    private BusinessException invalidSubject() {
        return new BusinessException(
                HttpStatus.UNAUTHORIZED,
                "AUTH_INVALID_CREDENTIALS",
                "인증 정보가 유효하지 않습니다."
        );
    }
}
