/**
 * 服务费 / 预算计算工具（前端）
 *
 * 与后端 com.task.platform.admin.util.FeeCalculator 保持同一口径，
 * 所有结果四舍五入保留 2 位小数（HALF_UP）：
 *   budget           = round(reward × (1 + rate) × quota, 2)
 *   singleServiceFee = round(reward × rate, 2)
 *   singleCost       = round(reward + singleServiceFee, 2)
 *
 * 说明：预算为「只读实时预览」，仅供前端展示参考；
 * 最终落库 budgetPoints 以后端用商户费率权威重算为准。
 */

/** 默认服务费率（商户未配置时），0.15 = 15% */
export const DEFAULT_FEE_RATE = 0.15

/** 四舍五入到 2 位小数（HALF_UP） */
export function round2(value: number): number {
  if (value == null || isNaN(value)) return 0
  return Math.round(value * 100) / 100
}

/** 归整费率：null/undefined 时使用默认 0.15 */
function rateOf(rate?: number | null): number {
  return rate != null && !isNaN(rate) ? rate : DEFAULT_FEE_RATE
}

/** 预算 = 单次奖励 × (1 + 服务费率) × 总配额（含服务费） */
export function computeBudget(reward: number, rate?: number | null, quota?: number | null): number {
  const r = rateOf(rate)
  const q = quota != null && quota > 0 ? quota : 1
  const safeReward = reward != null && !isNaN(reward) ? reward : 0
  return round2(safeReward * (1 + r) * q)
}

/** 单笔服务费 = 单次奖励 × 服务费率 */
export function computeSingleServiceFee(reward: number, rate?: number | null): number {
  const r = rateOf(rate)
  const safeReward = reward != null && !isNaN(reward) ? reward : 0
  return round2(safeReward * r)
}

/** 单笔含费成本 = 单次奖励 + 单笔服务费 */
export function computeSingleCost(reward: number, rate?: number | null): number {
  const r = rateOf(rate)
  const safeReward = reward != null && !isNaN(reward) ? reward : 0
  const fee = round2(safeReward * r)
  return round2(safeReward + fee)
}
