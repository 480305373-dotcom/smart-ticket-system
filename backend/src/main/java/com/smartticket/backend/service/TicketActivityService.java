package com.smartticket.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartticket.backend.entity.*;
import com.smartticket.backend.mapper.*;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TicketActivityService {
  private final TicketCommentMapper comments;
  private final TicketHistoryMapper histories;
  private final UserDirectoryService directory;

  public TicketActivityService(
      TicketCommentMapper comments, TicketHistoryMapper histories, UserDirectoryService directory) {
    this.comments = comments;
    this.histories = histories;
    this.directory = directory;
  }

  public List<TicketComment> comments(Long ticketId) {
    var list =
        comments.selectList(
            new LambdaQueryWrapper<TicketComment>()
                .eq(TicketComment::getTicketId, ticketId)
                .orderByAsc(TicketComment::getCreatedAt, TicketComment::getId));
    var names = directory.names(list.stream().map(TicketComment::getUserId).toList());
    list.forEach(c -> c.setUsername(names.getOrDefault(c.getUserId(), "已删除用户")));
    return list;
  }

  public TicketComment comment(Long ticketId, Long userId, String content) {
    var c = new TicketComment();
    c.setTicketId(ticketId);
    c.setUserId(userId);
    c.setContent(content.trim());
    comments.insert(c);
    c.setUsername(directory.names(java.util.List.of(userId)).get(userId));
    return c;
  }

  public List<TicketHistory> history(Long ticketId) {
    var list =
        histories.selectList(
            new LambdaQueryWrapper<TicketHistory>()
                .eq(TicketHistory::getTicketId, ticketId)
                .orderByAsc(TicketHistory::getCreatedAt, TicketHistory::getId));
    var ids =
        list.stream()
            .flatMap(
                h ->
                    java.util.stream.Stream.of(
                        h.getOperatorId(), h.getOldAssigneeId(), h.getNewAssigneeId()))
            .toList();
    var names = directory.names(ids);
    list.forEach(
        h -> {
          h.setUsername(names.get(h.getOperatorId()));
          h.setOldAssigneeUsername(
              h.getOldAssigneeId() == null ? null : names.get(h.getOldAssigneeId()));
          h.setNewAssigneeUsername(
              h.getNewAssigneeId() == null ? null : names.get(h.getNewAssigneeId()));
        });
    return list;
  }

  public void record(
      Long ticketId, Long operator, String action, String oldStatus, String newStatus) {
    var h = new TicketHistory();
    h.setTicketId(ticketId);
    h.setOperatorId(operator);
    h.setAction(action);
    h.setOldStatus(oldStatus);
    h.setNewStatus(newStatus);
    histories.insert(h);
  }

  public void recordAssignment(Long ticketId, Long operator, Long oldAssignee, Long newAssignee) {
    var history = new TicketHistory();
    history.setTicketId(ticketId);
    history.setOperatorId(operator);
    history.setAction("ASSIGN");
    history.setOldAssigneeId(oldAssignee);
    history.setNewAssigneeId(newAssignee);
    histories.insert(history);
  }
}
