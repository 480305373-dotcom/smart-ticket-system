package com.smartticket.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartticket.backend.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}