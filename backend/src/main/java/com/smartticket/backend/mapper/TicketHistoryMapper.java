package com.smartticket.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartticket.backend.entity.TicketHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketHistoryMapper extends BaseMapper<TicketHistory> {}
