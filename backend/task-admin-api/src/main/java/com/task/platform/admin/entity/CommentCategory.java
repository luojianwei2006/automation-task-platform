package com.task.platform.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_comment_category")
public class CommentCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer isDefault;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
