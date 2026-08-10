package com.retail.business.dto.resp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI Agent 长期记忆批量抽取/巩固操作结果响应(Java←Python 内部 DTO,Java 落库消费);包含一批次 N 条 operation + Python 执行成功/失败标记 + 说明.
 * <p>幂等:Java 消费时按 (operation.targetId, operation.op) 去重;HTTP 200+ok=true 才推进 last_extracted_id 游标;ok=false 下次重试同批.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemoryExtractResp {

    /** 本批次抽取/巩固产出的操作列表(1:N;空列表 = Python 侧无新记忆需抽,合法正常返回). */
    private List<MemoryOperationResp> operations = new ArrayList<>();

    /** Python 侧业务成功标记(true = 抽/巩固完成可推进游标;false = Python 抛异常,Java 不推进游标下次重抽同批). */
    private Boolean ok = true;

    /** 附加说明(ok=false 时填错误栈摘要;ok=true 可选填 "memory disabled" 等业务备注). */
    private String message = "";
}
