"""
config/base_settings.py
基础服务、端口、环境通用配置.
配置拆分原则：本文件仅承载服务级通用配置，避免与 LLM/存储/观测等配置耦合.
环境切换逻辑：APP_ENV=prod 读取 .env.prod，其余读取 .env（收敛到 config._env.get_env_file）.
"""
from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

from config._env import get_env_file

# 环境文件选择逻辑收敛到 config._env，消除 7 处重复定义
_ENV_FILE = get_env_file()


class AppBaseSettings(BaseSettings):
    """基础服务通用配置项.

    类名使用 AppBaseSettings 避免与 pydantic_settings.BaseSettings 同名遮蔽.
    """

    model_config = SettingsConfigDict(
        env_file=_ENV_FILE,
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # 当前运行环境标识：dev / prod
    APP_ENV: str = "dev"
    # 服务监听地址（Windows 本地开发固定 127.0.0.1）
    SERVICE_HOST: str = "127.0.0.1"
    # 服务监听端口
    SERVICE_PORT: int = 8000
    # 服务名称，用于日志与指标打点
    SERVICE_NAME: str = "service-python-agent"
    # 调试模式开关：开启后日志级别自动降为 DEBUG
    DEBUG: bool = True
    # 日志级别
    LOG_LEVEL: str = "DEBUG"
    # 内部调用密钥: Python 工具回调 Java API 时携带, Java 端校验后建立临时登录态,
    # 使 @SaCheckPermission 基于 userId 校验权限, 无需透传用户 Token.
    # 两端必须一致, 仅限内网调用.
    # 【必配项】无默认值（空串），启动时 validate_required_settings() 校验非空 + 非占位符.
    # 源码不含真实密钥，必须从 .env 读取，避免源码泄露即密钥泄露.
    INTERNAL_SECRET: str = Field(
        default="",
        description="Python↔Java 内部调用密钥（必配，从 .env 读取，两端必须一致）",
    )


# 全局基础配置单例
base_settings = AppBaseSettings()
