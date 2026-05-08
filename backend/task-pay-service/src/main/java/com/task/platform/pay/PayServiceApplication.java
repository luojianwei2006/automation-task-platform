package com.task.platform.pay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 支付服务启动类
 *
 * 职责：
 * 1. 余额管理（充值/扣减/查询）
 * 2. 提现审核与打款
 * 3. 对账（微信支付/支付宝）
 * 4. 任务奖励发放
 */
@SpringBootApplication
public class PayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayServiceApplication.class, args);
    }
}
