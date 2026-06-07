package com.task.platform.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_comment_word")
public class CommentWord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String content;
    private LocalDateTime createdAt;
}
