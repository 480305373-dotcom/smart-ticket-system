package com.smartticket.backend.dto;

import jakarta.validation.constraints.*;

public record ArticleRequest(
    @NotBlank(message = "标题不能为空") @Size(max = 200) String title,
    @NotBlank(message = "摘要不能为空") @Size(max = 500) String summary,
    @NotBlank(message = "正文不能为空") @Size(max = 50000) String content,
    @NotBlank(message = "分类不能为空") @Size(max = 100) String category,
    @NotBlank(message = "关键词不能为空") @Size(max = 500) String keywords,
    @NotNull @Pattern(regexp = "DRAFT|PUBLISHED", message = "文章状态只能为DRAFT或PUBLISHED")
        String status) {}
