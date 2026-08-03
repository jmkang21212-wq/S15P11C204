package com.example.laive.media.exception;

import com.example.laive.common.exception.BusinessException;

/**
 * 클립·녹화 도메인의 오류 코드를 공통 BusinessException으로 전달한다.
 */
public class MediaException extends BusinessException {

    private MediaException(MediaErrorCode errorCode) {
        super(errorCode.status(), errorCode.name(), errorCode.message());
    }

    private MediaException(MediaErrorCode errorCode, Throwable cause) {
        super(errorCode.status(), errorCode.name(), errorCode.message(), cause);
    }

    public static MediaException sessionNotFound() {
        return new MediaException(MediaErrorCode.RECORDING_SESSION_NOT_FOUND);
    }

    public static MediaException recordingNotFound() {
        return new MediaException(MediaErrorCode.RECORDING_NOT_FOUND);
    }

    public static MediaException highlightNotFound() {
        return new MediaException(MediaErrorCode.HIGHLIGHT_NOT_FOUND);
    }

    public static MediaException notPending() {
        return new MediaException(MediaErrorCode.CLIP_NOT_PENDING);
    }

    public static MediaException invalidFile() {
        return new MediaException(MediaErrorCode.CLIP_INVALID_FILE);
    }

    public static MediaException tooLarge() {
        return new MediaException(MediaErrorCode.CLIP_TOO_LARGE);
    }

    public static MediaException storageFailed(Throwable cause) {
        return new MediaException(MediaErrorCode.CLIP_STORAGE_FAILED, cause);
    }
}
