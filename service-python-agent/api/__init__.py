"""
api: 对外 HTTP 路由聚合包.

按业务域拆分 router, 由 main.py 按需 include_router.
当前包含:
- kb_sync_router: 接收 Java 侧知识库同步事件 (内部服务间调用, 非 RBAC 路由).
"""
