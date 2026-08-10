package com.retail.core.exception;

import com.retail.core.enums.ErrCodeEnum;

/**
 * Agent 下游服务远程调用异常.
 * <p>触发时机: Python Agent 网关不可达或调用失败时 throw (如 {@code AgentHttpClient} 捕获 RestClientException).
 * <p>解决的问题: 将下游不可用转化为受控异常, 由 {@code GlobalExceptionHandler} 统一转 R 响应,
 * 避免向上抛裸 RuntimeException (铁律 14).
 * <p>使用约束: msg 面向用户提示 (如"服务暂不可用"), 不得泄漏下游地址/堆栈 (铁律 15);
 * 默认错误码由 {@code ErrCodeEnum.AGENT_SERVICE_DOWN} 提供.
 */
public class AgentRemoteException extends BizException {
    // 默认:服务不可达
    public AgentRemoteException() {
        super(ErrCodeEnum.AGENT_SERVICE_DOWN);
    }
    // 自定义错误描述
    public AgentRemoteException(String customMsg) {
        super(ErrCodeEnum.AGENT_SERVICE_DOWN, customMsg);
    }
}
