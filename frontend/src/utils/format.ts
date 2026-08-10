// ============================================================
// 格式化工具：日期、金额、百分比、数量
// ============================================================
import dayjs from 'dayjs'

/** 日期时间格式化（默认 YYYY-MM-DD HH:mm:ss） */
export function formatDateTime(value: string | number | Date | null | undefined, fmt = 'YYYY-MM-DD HH:mm:ss'): string {
  if (!value) return '-'
  const d = dayjs(value)
  return d.isValid() ? d.format(fmt) : '-'
}

/** 日期格式化（YYYY-MM-DD） */
export function formatDate(value: string | number | Date | null | undefined): string {
  return formatDateTime(value, 'YYYY-MM-DD')
}

/** 金额格式化（保留 2 位小数，加 ¥ 前缀） */
export function formatMoney(value: number | string | null | undefined, prefix = '¥'): string {
  if (value === null || value === undefined || value === '') return '-'
  const num = Number(value)
  if (Number.isNaN(num)) return '-'
  return `${prefix}${num.toFixed(2)}`
}

/** 百分比格式化（0.85 → 85%） */
export function formatPercent(value: number | null | undefined, digits = 1): string {
  if (value === null || value === undefined) return '-'
  return `${value .toFixed(digits)}%`
}

/** 数量格式化（千分位） */
export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined) return '-'
  return Number(value).toLocaleString('zh-CN')
}

/** 星级显示 */
export function formatRating(rating: number | null | undefined): string {
  if (!rating) return '-'
  return '★'.repeat(Math.min(5, Math.max(0, rating))) + '☆'.repeat(Math.max(0, 5 - rating))
}

/** 截断文本 */
export function truncate(text: string | null | undefined, max = 50): string {
  if (!text) return '-'
  return text.length > max ? text.slice(0, max) + '…' : text
}
