package com.smartticket.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartticket.backend.entity.OperationLog;
import com.smartticket.backend.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;

@Service
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public OperationLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    public void record(
            Long userId,
            String action,
            Long targetId,
            String detail
    ) {
        OperationLog log = new OperationLog();

        log.setUserId(userId);
        log.setAction(action);
        log.setTargetType("TICKET");
        log.setTargetId(targetId);
        log.setDetail(detail);

        operationLogMapper.insert(log);
    }

    public IPage<OperationLog> getPage(long current, long size) {
        TicketService.validatePage(current, size);
        return operationLogMapper.selectPage(
                new Page<>(current, size),
                null
        );
    }
}
