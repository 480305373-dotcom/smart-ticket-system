package com.smartticket.backend.mapper;

import com.smartticket.backend.entity.TicketRequest;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TicketRequestMapper {
  @Insert("INSERT INTO ticket_request(user_id,request_id,fingerprint) VALUES (#{userId},#{requestId},#{fingerprint}) ON CONFLICT(user_id,request_id) DO NOTHING")
  int claim(@Param("userId") Long userId, @Param("requestId") String requestId, @Param("fingerprint") String fingerprint);
  @Select("SELECT user_id,request_id,fingerprint,ticket_id FROM ticket_request WHERE user_id=#{userId} AND request_id=#{requestId}")
  TicketRequest find(@Param("userId") Long userId, @Param("requestId") String requestId);
  @Update("UPDATE ticket_request SET ticket_id=#{ticketId} WHERE user_id=#{userId} AND request_id=#{requestId}")
  int complete(@Param("userId") Long userId, @Param("requestId") String requestId, @Param("ticketId") Long ticketId);
}
