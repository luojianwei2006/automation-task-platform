package com.task.platform.admin.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 服务费 / 预算计算工具（视频发布任务）
 *
 * <p>预算口径（唯一权威公式，前后端必须保持一致）：</p>
 * <ul>
 *     <li>budget           = round(reward × (1 + rate) × quota, 2, HALF_UP)</li>
 *     <li>singleServiceFee = round(reward × rate, 2, HALF_UP)</li>
 *     <li>singleCost       = round(reward + singleServiceFee, 2, HALF_UP)</li>
 * </ul>
 *
 * <p>费率取值：merchant.service_fee_rate（默认 0.15），经 task → project → merchant 解析。</p>
 *
 * @author TaskPlatform
 */
public final class FeeCalculator {

    /** 默认服务费率（商户未配置时使用），0.15 = 15% */
    public static final BigDecimal DEFAULT_FEE_RATE = new BigDecimal("0.15");

    /** 金额保留小数位 */
    private static final int SCALE = 2;

    private FeeCalculator() {
    }

    /**
     * 归整费率：为 null 时使用默认费率 0.15
     */
    private static BigDecimal rateOf(BigDecimal rate) {
        return rate != null ? rate : DEFAULT_FEE_RATE;
    }

    /**
     * 四舍五入到 2 位小数（HALF_UP）
     */
    private static BigDecimal round(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 预算 = 单次奖励 × (1 + 服务费率) × 总配额（含服务费），四舍五入保留 2 位。
     *
     * @param reward 单次奖励金额（可为 null，按 0 处理）
     * @param rate   服务费率（可为 null，按默认 0.15 处理）
     * @param quota  总配额（≤0 时按 1 处理）
     * @return 预算点数（含服务费）
     */
    public static BigDecimal computeBudget(BigDecimal reward, BigDecimal rate, Integer quota) {
        BigDecimal r = rateOf(rate);
        int q = (quota != null && quota > 0) ? quota : 1;
        BigDecimal safeReward = reward != null ? reward : BigDecimal.ZERO;
        BigDecimal factor = BigDecimal.ONE.add(r);
        return round(safeReward.multiply(factor).multiply(BigDecimal.valueOf(q)));
    }

    /**
     * 单笔服务费 = 单次奖励 × 服务费率，四舍五入保留 2 位。
     */
    public static BigDecimal computeSingleServiceFee(BigDecimal reward, BigDecimal rate) {
        BigDecimal r = rateOf(rate);
        BigDecimal safeReward = reward != null ? reward : BigDecimal.ZERO;
        return round(safeReward.multiply(r));
    }

    /**
     * 单笔含费成本 = 单次奖励 + 单笔服务费，四舍五入保留 2 位。
     */
    public static BigDecimal computeSingleCost(BigDecimal reward, BigDecimal rate) {
        BigDecimal r = rateOf(rate);
        BigDecimal safeReward = reward != null ? reward : BigDecimal.ZERO;
        BigDecimal fee = round(safeReward.multiply(r));
        return round(safeReward.add(fee));
    }
}
