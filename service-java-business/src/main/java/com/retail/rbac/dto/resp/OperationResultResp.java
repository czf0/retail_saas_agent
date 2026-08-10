package com.retail.rbac.dto.resp;

import lombok.Data;

/**
 * 通用操作结果响应(C/U/D 类非查询接口统一返回体);包含操作是否成功 + 前端 toast 提示信息.
 * <p>适用于:删除,启停,状态切换,批量导入/导出触发,缓存刷新等无返回实体的简单 Action 接口;需要返回业务 ID 时改用 XxxCreateResp 等专用响应.
 */
@Data
public class OperationResultResp {

    /** true = 操作成功(前端绿勾 toast);false = 业务失败(非 HTTP 异常层,用于"部分成功/软校验失败"如批量导入部分行失败). */
    private Boolean success;

    /** 操作提示信息(前端直接展示;success=true 时默认"操作成功",false 时为失败原因;i18n 由后端返回已翻译文本). */
    private String message;
}
