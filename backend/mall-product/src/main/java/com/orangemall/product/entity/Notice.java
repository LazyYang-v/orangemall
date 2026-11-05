package com.orangemall.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("notice")
public class Notice {
    private String id;
    private String title;
    private String date;
    private String content;
}
