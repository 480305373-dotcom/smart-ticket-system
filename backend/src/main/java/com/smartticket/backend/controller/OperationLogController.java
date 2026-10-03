package com.smartticket.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartticket.backend.dto.ApiResponse;
import com.smartticket.backend.entity.OperationLog;
import com.smartticket.backend.security.AuthUser;
import com.smartticket.backend.service.OperationLogService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(
            OperationLogService operationLogService
    ) {
        this.operationLogService = operationLogService;
    }

    @GetMapping("/operation-logs")
    public ApiResponse<IPage<OperationLog>> getLogs(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            Authentication authentication
    ) {
        AuthUser user =
                (AuthUser) authentication.getPrincipal();

        if (!"ADMIN".equals(user.role())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "只有管理员可以查看操作日志"
            );
        }

        return ApiResponse.success(
                operationLogService.getPage(current, size)
        );
    }
}