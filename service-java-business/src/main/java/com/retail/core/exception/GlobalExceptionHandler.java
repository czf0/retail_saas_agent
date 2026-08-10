package com.retail.core.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.retail.core.enums.ErrCodeEnum;
import com.retail.core.result.R;
import com.retail.core.trace.TraceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器 — 统一异常 → R 响应转换.
 * <p>
 * 设计原则 (三端错误码整改):
 * <ol>
 *   <li><b>日志/对外分离</b>: 技术详情 (权限码,字段名,异常类名,参数值) 仅写入日志;
 *       对外返回 ErrCodeEnum 预设的面向用户提示.</li>
 *   <li><b>BizException 透传</b>: Service 层 ParamException / AuthException 等的 msg 已面向用户编写,
 *       直接透传 code + msg.</li>
 *   <li><b>兜底安全</b>: 未知异常返回通用"服务暂时不可用",不泄漏内部异常类名/消息.</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TenantException.class)
    public R<Object> handleTenantException(TenantException e) {
        R<Object> r = R.fail(e.getCode(), e.getMsg());
        r.setTraceId(TraceUtil.getTraceId());
        return r;
    }

    @ExceptionHandler(AuthException.class)
    public R<Object> handleAuthException(AuthException e) {
        R<Object> r = R.fail(e.getCode(), e.getMsg());
        r.setTraceId(TraceUtil.getTraceId());
        return r;
    }

    @ExceptionHandler(AgentRemoteException.class)
    public R<Object> handleAgentException(AgentRemoteException e) {
        R<Object> r = R.fail(e.getCode(), e.getMsg());
        r.setTraceId(TraceUtil.getTraceId());
        return r;
    }

    @ExceptionHandler(ParamException.class)
    public R<Object> handleParamException(ParamException e) {
        // ParamException 的 msg 已由 Service 层面向用户编写, 直接透传
        R<Object> r = R.fail(e.getCode(), e.getMsg());
        r.setTraceId(TraceUtil.getTraceId());
        return r;
    }

    // 兜底通用业务异常
    @ExceptionHandler(BizException.class)
    public R<Object> handleBizException(BizException e) {
        // BizException 的 msg 已由 Service 层面向用户编写, 直接透传
        R<Object> r = R.fail(e.getCode(), e.getMsg());
        r.setTraceId(TraceUtil.getTraceId());
        return r;
    }

    // Sa-Token 未登录 — 日志记类型, 对外返回友好提示
    @ExceptionHandler(NotLoginException.class)
    public R<Object> handleNotLoginException(NotLoginException e) {
        log.warn("Sa-Token 未登录 traceId={} type={}", TraceUtil.getTraceId(), e.getType());
        R<Object> r = R.fail(ErrCodeEnum.AUTH_NOT_LOGIN);
        r.setTraceId(TraceUtil.getTraceId());
        return r;
    }

    // Sa-Token 无权限 — 日志记权限码 (开发者排查), 对外返回通用提示
    // @ResponseStatus(HttpStatus.FORBIDDEN):越权访问返回 HTTP 403,符合 REST 语义;
    // 同时保留业务码 20002 供前端统一解析 R.code.
    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Object> handleNotPermissionException(NotPermissionException e) {
        // 记录真正缺失的权限标识(e.getPermission()),而非 Sa-Token 内部常量码(e.getCode(),恒为11051),
        // 否则无法从日志定位到底是哪个权限串未授予
        log.warn("Sa-Token 权限不足 traceId={} needPermission={}", TraceUtil.getTraceId(), e.getPermission());
        R<Object> r = R.fail(ErrCodeEnum.AUTH_PERMISSION_DENIED);
        r.setTraceId(TraceUtil.getTraceId());
        return r;
    }

    // Sa-Token 无所需角色 — 日志记角色标识 (开发者排查), 对外返回通用提示
    // @ResponseStatus(HttpStatus.FORBIDDEN):角色不足同样返回 HTTP 403.
    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Object> handleNotRoleException(NotRoleException e) {
        log.warn("Sa-Token 角色不足 traceId={} role={}", TraceUtil.getTraceId(), e.getRole());
        R<Object> r = R.fail(ErrCodeEnum.AUTH_ROLE_DENIED);
        r.setTraceId(TraceUtil.getTraceId());
        return r;
    }

    /**
     * 参数校验失败(@Valid + @RequestBody 触发).
     * <p>字段级错误详情写入日志 (开发者排查), 对外返回通用提示.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败 traceId={} detail={}", TraceUtil.getTraceId(), detail);
        R<Object> r = R.fail(ErrCodeEnum.PARAM_INVALID);
        r.setTraceId(TraceUtil.getTraceId());
        return r;
    }

    /**
     * 表单参数绑定失败(GET 请求 model 绑定触发).
     * <p>错误详情写入日志, 对外返回通用提示.
     */
    @ExceptionHandler(BindException.class)
    public R<Object> handleBindException(BindException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败 traceId={} detail={}", TraceUtil.getTraceId(), detail);
        R<Object> r = R.fail(ErrCodeEnum.PARAM_INVALID);
        r.setTraceId(TraceUtil.getTraceId());
        return r;
    }

    /**
     * 路径变量类型转换失败(如 /{id} 传 "all" 但 id 是 Long).
     * <p>参数名和传入值写入日志, 对外返回通用提示.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Object> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配 traceId={} param={} value={}", TraceUtil.getTraceId(), e.getName(), e.getValue());
        R<Object> r = R.fail(ErrCodeEnum.PARAM_TYPE_MISMATCH);
        r.setTraceId(TraceUtil.getTraceId());
        return r;
    }

    /**
     * 系统未知异常兜底: 永久记录完整堆栈到日志, 对外返回通用提示 + traceId 便于排查.
     * <p>响应消息保持通用"服务暂时不可用",不泄漏内部异常类名/消息到客户端 (安全).
     */
    @ExceptionHandler(Exception.class)
    public R<Object> handleAllException(Exception e) {
        log.error("未捕获异常 traceId={} type={} msg={}", TraceUtil.getTraceId(),
                e.getClass().getSimpleName(), e.getMessage(), e);
        R<Object> r = R.fail(ErrCodeEnum.SYSTEM_INTERNAL_ERROR);
        r.setTraceId(TraceUtil.getTraceId());
        return r;
    }
}
