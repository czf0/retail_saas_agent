"""
config/_env.py
环境切换逻辑收敛：消除 7 个 settings 文件中重复的 _ENV_FILE 定义.

设计说明：
- 原先每个 settings 文件各自写 `_ENV_FILE = ".env.prod" if os.getenv("APP_ENV") == "prod" else ".env"`，
  修改环境切换逻辑需改 7 处，易遗漏不一致；
- 本文件集中提供 get_env_file()，各 settings 文件 import 复用，单一改动点；
- APP_ENV=prod 加载 .env.prod（生产配置），其余（含未设置）加载 .env（开发默认）.
"""
import os


def get_env_file() -> str:
    """根据 APP_ENV 环境标识返回对应的 env 文件路径.

    Returns:
        ".env.prod" 当 APP_ENV=prod；否则 ".env"（dev 默认）.
    """
    return ".env.prod" if os.getenv("APP_ENV", "dev") == "prod" else ".env"
