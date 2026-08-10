"""
config/llm_settings.py
LLM 相关参数独立拆分，承载模型调用、超时、批量并发等配置.
环境切换逻辑收敛到 config._env.get_env_file.
"""
from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

from config._env import get_env_file

# 环境文件选择逻辑收敛到 config._env，消除 7 处重复定义
_ENV_FILE = get_env_file()


class LLMSettings(BaseSettings):
    """LLM 调用相关配置项."""

    model_config = SettingsConfigDict(
        env_file=_ENV_FILE,
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # LLM 服务基础地址（OpenAI 兼容协议）
    LLM_BASE_URL: str = "https://open.bigmodel.cn/api/paas/v4"
    # 【必配项】LLM API Key，无默认值（空串），启动时 validate_required_settings() 校验非空 + 非占位符.
    # 源码不含真实密钥，必须从 .env 读取，避免源码泄露即密钥泄露.
    LLM_API_KEY: str = Field(
        default="",
        description="LLM 服务商 API Key（必配，从 .env 读取）",
    )
    # 默认对话模型名称 (glm-4-flash: 免费且支持 function calling 流式; glm-4.7 推理模型与 LangChain bind_tools 流式不兼容)
    LLM_MODEL: str = "glm-4-flash"
    # 单次回答最大 Token
    LLM_MAX_TOKENS: int = 2048
    # 采样温度
    LLM_TEMPERATURE: float = 0.2
    # 单次请求超时时间（秒）
    LLM_TIMEOUT: int = 60
    # 批量异步调用并发数
    LLM_BATCH_CONCURRENCY: int = 4


# 全局 LLM 配置单例
llm_settings = LLMSettings()
