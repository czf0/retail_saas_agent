package com.retail.core.aspect;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 访问日志切面:统一记录所有 Controller HTTP 请求的方法/URL/参数/响应状态/耗时.
 * <p>
 * 切点为 {@code @within(RestController)},覆盖所有 {@code @RestController} 注解的类.
 * <p>
 * <b>日志分级策略</b>:
 * <ul>
 *   <li>写操作(POST/PUT/DELETE):INFO 级,含入参摘要(脱敏后),便于审计与回溯</li>
 *   <li>读操作(GET):DEBUG 级,仅记录 URI + 耗时,避免查询日志爆炸</li>
 *   <li>慢请求(&gt;500ms):WARN 级单独标记,便于性能问题定位</li>
 *   <li>异常请求:ERROR 级 + 异常类名 + 消息,异常原样抛出交给 GlobalExceptionHandler</li>
 * </ul>
 * <p>
 * <b>参数脱敏</b>:入参 JSON 中字段名匹配 {@link #SENSITIVE_KEYS} 的值替换为 {@code ***},
 * 使用正则替换避免递归遍历对象的性能开销.
 * <p>
 * <b>豁免路径</b>:/actuator/** 与 /error 不记录(健康检查噪音,Spring 错误端点).
 */
@Aspect
@Component
@Slf4j
public class AccessLogAspect {

    /** 慢请求阈值(毫秒),超过则升级为 WARN 级 */
    private static final long SLOW_THRESHOLD_MS = 500L;

    /** 入参 JSON 序列化最大长度(字符),超出截断避免日志过大 */
    private static final int MAX_ARGS_LENGTH = 2000;

    /** 豁免路径前缀(不记录访问日志) */
    private static final Set<String> SKIP_PREFIXES = Set.of("/actuator", "/error", "/favicon.ico");

    /** 敏感字段名正则(匹配 password/token/secret/cardNo/cvv 等及其变体) */
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(\"(?:password|passwd|pwd|token|secret|cardNo|cardno|cvv|idCard|mobile|phone)\"\\s*:\\s*)\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    /** Jackson ObjectMapper(禁用 fail-on-empty-beans,避免序列化异常) */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    /** 记录已警告过序列化失败的类,避免重复日志 */
    private static final Set<Class<?>> SER_WARNED = new HashSet<>();

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            // 非 HTTP 请求上下文(如异步线程内调用),直接放行
            return pjp.proceed();
        }

        String uri = request.getRequestURI();
        if (shouldSkip(uri)) {
            return pjp.proceed();
        }

        String httpMethod = request.getMethod();
        String clientIp = clientIp(request);
        boolean isWrite = isWriteMethod(httpMethod);
        String methodSig = pjp.getSignature().getDeclaringType().getSimpleName() + "." + pjp.getSignature().getName();
        long start = System.currentTimeMillis();

        try {
            Object result = pjp.proceed();
            long cost = System.currentTimeMillis() - start;
            logSuccess(httpMethod, uri, methodSig, clientIp, isWrite, cost, pjp.getArgs());
            return result;
        } catch (Throwable e) {
            long cost = System.currentTimeMillis() - start;
            logFailure(httpMethod, uri, methodSig, clientIp, isWrite, cost, e);
            // 异常原样抛出,不吞异常,交给 GlobalExceptionHandler 统一处理
            throw e;
        }
    }

    /** 记录成功请求:写操作 INFO(含入参),读操作 DEBUG,慢请求升级 WARN */
    private void logSuccess(String httpMethod, String uri, String methodSig, String ip,
                            boolean isWrite, long cost, Object[] args) {
        boolean isSlow = cost > SLOW_THRESHOLD_MS;
        if (isSlow) {
            log.warn("[SLOW] [{}] {} {} ip={} args={} cost={}ms status=ok (慢请求>{})",
                    httpMethod, uri, methodSig, ip, maskArgs(args), cost, SLOW_THRESHOLD_MS);
            return;
        }
        if (isWrite) {
            log.info("[{}] {} {} ip={} args={} cost={}ms status=ok",
                    httpMethod, uri, methodSig, ip, maskArgs(args), cost);
        } else {
            log.debug("[{}] {} {} ip={} cost={}ms status=ok",
                    httpMethod, uri, methodSig, ip, cost);
        }
    }

    /** 记录异常请求:ERROR 级 + 异常类名 + 消息 */
    private void logFailure(String httpMethod, String uri, String methodSig, String ip,
                            boolean isWrite, long cost, Throwable e) {
        log.error("[{}] {} {} ip={} args={} cost={}ms status=error type={} msg={}",
                httpMethod, uri, methodSig, ip, maskArgs(isWrite ? new Object[]{e.getMessage()} : new Object[0]),
                cost, e.getClass().getSimpleName(), e.getMessage());
    }

    /** 判断是否写操作(POST/PUT/DELETE/PATCH) */
    private boolean isWriteMethod(String httpMethod) {
        return "POST".equalsIgnoreCase(httpMethod)
                || "PUT".equalsIgnoreCase(httpMethod)
                || "DELETE".equalsIgnoreCase(httpMethod)
                || "PATCH".equalsIgnoreCase(httpMethod);
    }

    /** 豁免路径判断:actuator / error / favicon */
    private boolean shouldSkip(String uri) {
        if (StrUtil.isBlank(uri)) {
            return true;
        }
        for (String prefix : SKIP_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 入参序列化 + 脱敏.
     * <p>使用 Jackson 序列化入参数组为 JSON,对敏感字段值正则替换为 ***,
     * 超过 {@link #MAX_ARGS_LENGTH} 截断并标记 {@code ...}.
     * 序列化失败时回退为 args 数组的 toString,避免影响业务.
     */
    private String maskArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(args);
            // 敏感字段值替换为 ***
            Matcher m = SENSITIVE_PATTERN.matcher(json);
            String masked = m.replaceAll("$1\"***\"");
            if (masked.length() > MAX_ARGS_LENGTH) {
                return masked.substring(0, MAX_ARGS_LENGTH) + "...";
            }
            return masked;
        } catch (Exception e) {
            // 序列化失败(如循环引用,不可序列化对象),回退简单 toString,避免日志切面影响业务
            Class<?> firstType = args[0] != null ? args[0].getClass() : null;
            if (firstType != null && SER_WARNED.add(firstType)) {
                log.debug("入参序列化失败，回退 toString type={} err={}", firstType.getName(), e.getMessage());
            }
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(args[i] == null ? "null" : args[i].toString());
            }
            sb.append("]");
            return sb.toString();
        }
    }

    /** 获取客户端真实 IP,穿透代理 X-Forwarded-For */
    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            // 多级代理时取第一个(最原始客户端)
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    /** 从 RequestContextHolder 获取当前线程的 HttpServletRequest */
    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }
}
