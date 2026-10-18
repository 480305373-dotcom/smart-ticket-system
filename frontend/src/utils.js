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
const dateFormatter = new Intl.DateTimeFormat("zh-CN", {
  timeZone: "Asia/Shanghai",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hourCycle: "h23",
});
export const date = (value) => {
  if (value == null || value === "") return "—";
  const text = String(value);
  if (
    value instanceof Date ||
    typeof value === "number" ||
    /(?:Z|[+-]\d{2}:?\d{2})$/i.test(text)
  ) {
    const instant = new Date(value);
    if (!Number.isFinite(instant.getTime())) return "—";
    const p = Object.fromEntries(
      dateFormatter
        .formatToParts(instant)
        .map((part) => [part.type, part.value]),
    );
    return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}:${p.second}`;
  }
  return text.replace("T", " ").slice(0, 19);
};
export const priorityLabel = (value) =>
  ["", "1 · 低", "2 · 一般", "3 · 中", "4 · 高", "5 · 紧急"][value] || "—";
