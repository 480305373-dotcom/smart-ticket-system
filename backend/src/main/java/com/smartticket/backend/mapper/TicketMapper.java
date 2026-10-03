package com.smartticket.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartticket.backend.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {
    @org.apache.ibatis.annotations.Select("""
        SELECT count(*) AS total,
          count(*) FILTER (WHERE status='OPEN') AS open,
          count(*) FILTER (WHERE status='IN_PROGRESS') AS in_progress,
          count(*) FILTER (WHERE status='RESOLVED') AS resolved,
          count(*) FILTER (WHERE status='CLOSED') AS closed,
          count(*) FILTER (WHERE priority>=4) AS high_priority,
          CASE WHEN #{admin} THEN (SELECT count(*) FROM app_user) ELSE 0 END AS user_count,
          (SELECT count(*) FROM knowledge_article WHERE #{admin} OR status='PUBLISHED') AS article_count
        FROM ticket WHERE #{admin} OR user_id=#{userId}
        """)
    com.smartticket.backend.dto.TicketStatistics statistics(
        @org.apache.ibatis.annotations.Param("userId") Long userId,
        @org.apache.ibatis.annotations.Param("admin") boolean admin);
}

