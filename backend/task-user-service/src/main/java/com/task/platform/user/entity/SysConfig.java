package com.task.platform.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_config")
public class SysConfig {
    private Long id;
    private String configKey;
    private String configValue;
    private String description;
}
