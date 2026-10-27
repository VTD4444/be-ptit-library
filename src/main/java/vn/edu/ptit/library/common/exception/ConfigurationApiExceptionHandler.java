package vn.edu.ptit.library.common.exception;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.edu.ptit.library.circulation.controller.PolicyAdminController;
import vn.edu.ptit.library.circulation.controller.PenaltyTypeAdminController;
import vn.edu.ptit.library.search.controller.SearchConfigAdminController;
import vn.edu.ptit.library.common.dto.ApiResponse;

// Limit input-error changes to the new configuration APIs.
@Order(-1)
@RestControllerAdvice(assignableTypes = {PolicyAdminController.class, PenaltyTypeAdminController.class,
        SearchConfigAdminController.class})
public class ConfigurationApiExceptionHandler {
    @ExceptionHandler({HttpMessageNotReadableException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> invalidInput(Exception ignored) {
        return ResponseEntity.badRequest().body(ApiResponse.error(
                ErrorCode.INVALID_REQUEST.getCode(), ErrorCode.INVALID_REQUEST.getDefaultMessage()));
    }
}
