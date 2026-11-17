package com.example.security.common;

/**
 * Unified API response wrapper.
 * All new endpoints return this format: { code, success, message, data }
 * Compatible with frontend checks: res.data.code === 200
 */
public class Result<T> {
    private Integer code;
    private Boolean success;
    private String message;
    private T data;

    public Result() {}

    public Result(Integer code, Boolean success, String message, T data) {
        this.code = code;
        this.success = success;
        this.message = message;
        this.data = data;
    }

    // ---- static factories ----

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, true, "操作成功", data);
    }

    public static <T> Result<T> ok(String message) {
        return new Result<>(200, true, message, null);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(200, true, message, data);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(500, false, message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, false, message, null);
    }

    // ---- getters / setters ----

    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
