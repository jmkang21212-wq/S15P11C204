package com.example.laive.platform.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 방송 플랫폼 연동 처리 중 발생한 HTTP 상태, 공개 오류 코드와 사용자 메시지를 함께 전달한다.
 * 오류 코드는 API 명세서 부록의 공통 오류 코드 표를 따른다.
 */
@Getter
public class PlatformException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public PlatformException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /** 플랫폼 OAuth 또는 API 호출 자체가 실패했다. */
    public static PlatformException connectionFailed() {
        return new PlatformException(
                HttpStatus.BAD_GATEWAY,
                "PLATFORM_CONNECTION_FAILED",
                "플랫폼 연동 처리에 실패했습니다."
        );
    }

    /** 저장된 토큰이 더는 유효하지 않아 사용자가 다시 연동해야 한다. */
    public static PlatformException reauthRequired() {
        return new PlatformException(
                HttpStatus.CONFLICT,
                "PLATFORM_REAUTH_REQUIRED",
                "플랫폼 연동이 만료되었습니다. 다시 연동해 주세요."
        );
    }

    /**
     * 연동 당시 승인받지 않은 권한이 필요한 요청이다. 토큰 자체는 유효하므로
     * 연동 상태는 건드리지 않으며, 다시 연동해 새 scope를 승인받아야 해소된다.
     */
    public static PlatformException scopeInsufficient() {
        return new PlatformException(
                HttpStatus.CONFLICT,
                "PLATFORM_SCOPE_INSUFFICIENT",
                "이 기능에 필요한 권한이 없어요. 플랫폼 채널을 다시 연동해 주세요."
        );
    }

    /** 다른 회원이 이미 연동한 채널이다. */
    public static PlatformException alreadyLinked() {
        return new PlatformException(
                HttpStatus.CONFLICT,
                "PLATFORM_ALREADY_LINKED",
                "이미 다른 계정에 연동된 채널입니다."
        );
    }

    /** 존재하지 않거나 본인 소유가 아닌 연동이다. */
    public static PlatformException notFound() {
        return new PlatformException(
                HttpStatus.NOT_FOUND,
                "PLATFORM_ACCOUNT_NOT_FOUND",
                "연동 정보를 찾을 수 없습니다."
        );
    }

    public static PlatformException invalidRequest(String message) {
        return new PlatformException(HttpStatus.BAD_REQUEST, "PLATFORM_INVALID_REQUEST", message);
    }
}
