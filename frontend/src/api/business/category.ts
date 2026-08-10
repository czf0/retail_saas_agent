// ============================================================
// 商品分类 API（对接 ProductCategoryController /api/v1/products/categories）
// 权限标识：business:category:{list,query,add,edit,remove}
// 二级树形结构
// ============================================================
import request from '@/api/request'
import type { OperationResultResp, TreeNode } from '@/api/types'

export interface ProductCategory extends TreeNode {
  name: string
  parentId?: number | null
  parentName?: string | null   // 父分类名称（后端 Service 层填充，根分类为 null）
  sortOrder: number
  status: number              // 1=active / 0=inactive
  description?: string | null
  createdAt: string
  children?: ProductCategory[]
  productCount?: number
}

export interface CategoryCreateReq {
  name: string
  parentId?: number | null
  sortOrder?: number
  status?: number
  description?: string
}

export interface CategoryUpdateReq extends CategoryCreateReq {}

export const categoryApi = {
  /** 获取分类树（activeOnly=true 仅返回启用分类） */
  tree: (activeOnly = false) => request.get<ProductCategory[]>('/products/categories', { params: { activeOnly } }),
  detail: (id: number) => request.get<ProductCategory>(`/products/categories/${id}`),
  create: (data: CategoryCreateReq) => request.post<ProductCategory>('/products/categories', data),
  update: (id: number, data: CategoryUpdateReq) => request.put<OperationResultResp>(`/products/categories/${id}`, data),
  remove: (id: number) => request.delete<OperationResultResp>(`/products/categories/${id}`)
}
