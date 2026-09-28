package com.securebank.banking.transfers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = TransferController.class)
public class TransferExceptionHandler {

    @ExceptionHandler(TransferService.Rejected.class)
    public ProblemDetail rejected(TransferService.Rejected exception) {
        HttpStatus status = switch (exception.code()) {
            case "CUSTOMER_UNAVAILABLE" -> HttpStatus.FORBIDDEN;
            case "ACCOUNT_UNAVAILABLE" -> HttpStatus.NOT_FOUND;
            case "INSUFFICIENT_FUNDS", "IDEMPOTENCY_CONFLICT" ->
                    HttpStatus.CONFLICT;
            case "SAME_ACCOUNT", "CURRENCY_MISMATCH" ->
                    HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        String detail = switch (exception.code()) {
            case "CUSTOMER_UNAVAILABLE" ->
                    "Your customer profile is unavailable for transfers.";
            case "ACCOUNT_UNAVAILABLE" ->
                    "One or both accounts are unavailable for this transfer.";
            case "INSUFFICIENT_FUNDS" ->
                    "The source account has insufficient funds.";
            case "IDEMPOTENCY_CONFLICT" ->
                    "This request key was already used with different details.";
            case "SAME_ACCOUNT" ->
                    "Choose two different accounts.";
            case "CURRENCY_MISMATCH" ->
                    "Both accounts must use the requested currency.";
            default ->
                    "The transfer could not be completed.";
        };

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", exception.code());
        return problem;
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ProblemDetail invalidRequest(Exception exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Check the account IDs, positive USD amount, description, "
                        + "and UUID Idempotency-Key header.");

        problem.setProperty("code", "INVALID_REQUEST");
        return problem;
    }
}
