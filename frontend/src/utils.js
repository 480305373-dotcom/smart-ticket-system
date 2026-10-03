export const statuses = {
  OPEN: "待处理",
  IN_PROGRESS: "处理中",
  RESOLVED: "已解决",
  CLOSED: "已关闭",
};
export const statusType = (value) =>
  ({
    OPEN: "warning",
    IN_PROGRESS: "primary",
    RESOLVED: "success",
    CLOSED: "info",
  })[value];
export const date = (value) =>
  value ? value.replace("T", " ").slice(0, 19) : "—";
export const priorityLabel = (value) =>
  ["", "1 · 低", "2 · 一般", "3 · 中", "4 · 高", "5 · 紧急"][value] || "—";
