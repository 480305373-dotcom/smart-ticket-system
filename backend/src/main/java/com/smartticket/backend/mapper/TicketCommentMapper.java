package com.smartticket.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartticket.backend.entity.TicketComment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketCommentMapper extends BaseMapper<TicketComment> {}
