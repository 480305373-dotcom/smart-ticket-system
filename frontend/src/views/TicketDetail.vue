<script setup>
import { ref, onMounted, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuth } from "../stores/auth";
import { ElMessage, ElMessageBox } from "element-plus";
import api from "../api";
import { date, statuses, statusType, priorityLabel } from "../utils";
const route = useRoute(),
  router = useRouter(),
  auth = useAuth(),
  ticket = ref(null),
  comments = ref([]),
  history = ref([]),
  articles = ref([]),
  content = ref(""),
  loading = ref(true),
  busy = ref(false),
  error = ref("");
const handlers = ref([]),
  assigneeId = ref(null);
const base = `/tickets/${route.params.id}`;
const next = computed(
  () =>
    ({ OPEN: "IN_PROGRESS", IN_PROGRESS: "CLOSED", RESOLVED: "CLOSED" })[
      ticket.value?.status
    ],
);
async function load() {
  loading.value = true;
  error.value = "";
  try {
    ticket.value = await api.get(base);
    assigneeId.value = ticket.value.assigneeId;
    const [c, h, k] = await Promise.all([
      api.get(`${base}/comments`),
      api.get(`${base}/history`),
      api.get(`${base}/knowledge-recommendations`),
    ]);
    comments.value = c;
    history.value = h;
    articles.value = k;
  } catch (e) {
    error.value =
      e.response?.status === 403
        ? "无权访问该工单。"
        : e.userMessage || "工单加载失败";
  } finally {
    loading.value = false;
  }
}
async function assign() {
  busy.value = true;
  try {
    await api.patch(`${base}/assignee`, {
      assigneeId: assigneeId.value || null,
    });
    ElMessage.success("处理人已更新");
    await load();
  } catch {
  } finally {
    busy.value = false;
  }
}
function historyLabel(h) {
  if (h.action === "CREATE") return "创建工单";
  if (h.action === "ASSIGN")
    return `指派：${h.oldAssigneeUsername || "未分配"} → ${h.newAssigneeUsername || "未分配"}`;
  return `${statuses[h.oldStatus]} → ${statuses[h.newStatus]}`;
}
async function change() {
  busy.value = true;
  try {
    await api.patch(`${base}/status`, { status: next.value });
    ElMessage.success("状态已更新");
    await load();
  } catch {
  } finally {
    busy.value = false;
  }
}
async function comment() {
  if (!content.value.trim()) return ElMessage.warning("留言不能为空");
  if (content.value.length > 2000)
    return ElMessage.warning("留言不能超过2000字");
  busy.value = true;
  try {
    await api.post(`${base}/comments`, { content: content.value });
    content.value = "";
    comments.value = await api.get(`${base}/comments`);
  } catch {
  } finally {
    busy.value = false;
  }
}
async function remove() {
  try {
    await ElMessageBox.confirm(
      "删除工单将同时删除该工单的评论和处理历史，确认继续？",
      "删除工单",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" },
    );
    await api.delete(base);
    ElMessage.success("工单已删除");
    router.push("/tickets");
  } catch {}
}
onMounted(async () => {
  await load();
  if (auth.isAdmin && ticket.value && !error.value) {
    try {
      handlers.value = await api.get("/users/handlers");
    } catch {}
  }
});
</script>
<template>
  <div v-loading="loading">
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
    /><template v-if="ticket && !error"
      ><section class="panel">
        <div class="panel-title">
          <div>
            <span class="eyebrow">TICKET #{{ ticket.id }}</span>
            <h2>{{ ticket.title }}</h2>
          </div>
          <div class="actions">
            <el-button @click="$router.push(`${base}/edit`)">编辑</el-button
            ><el-button
              v-if="auth.isAdmin && next"
              type="primary"
              :loading="busy"
              @click="change"
              >{{ next === "IN_PROGRESS" ? "开始处理" : "关闭工单" }}</el-button
            ><el-button type="danger" plain @click="remove">删除</el-button>
          </div>
        </div>
        <el-descriptions :column="2" border
          ><el-descriptions-item label="创建用户">{{
            ticket.username
          }}</el-descriptions-item
          ><el-descriptions-item label="当前状态"
            ><el-tag :type="statusType(ticket.status)">{{
              statuses[ticket.status]
            }}</el-tag></el-descriptions-item
          ><el-descriptions-item label="优先级">{{
            priorityLabel(ticket.priority)
          }}</el-descriptions-item
          ><el-descriptions-item label="工单编号"
            >#{{ ticket.id }}</el-descriptions-item
          ><el-descriptions-item label="创建时间">{{
            date(ticket.createdAt)
          }}</el-descriptions-item
          ><el-descriptions-item label="更新时间">{{
            date(ticket.updatedAt)
          }}</el-descriptions-item></el-descriptions
        >
        <h3>问题描述</h3>
        <p>
          当前处理人：<strong>{{ ticket.assigneeUsername || "未分配" }}</strong>
        </p>
        <el-form
          v-if="auth.isAdmin && ['OPEN', 'IN_PROGRESS'].includes(ticket.status)"
          inline
          @submit.prevent="assign"
        >
          <el-form-item label="指派处理人">
            <el-select
              v-model="assigneeId"
              clearable
              placeholder="未分配"
              aria-label="指派处理人"
              style="width: 200px"
            >
              <el-option
                v-for="handler in handlers"
                :key="handler.id"
                :label="handler.username"
                :value="handler.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            ><el-button
              type="primary"
              plain
              native-type="submit"
              :loading="busy"
              >保存指派</el-button
            ></el-form-item
          >
        </el-form>
        <p class="pre-wrap">{{ ticket.description || "未填写描述" }}</p>
      </section>
      <div class="detail-grid">
        <div>
          <section class="panel">
            <h2>沟通与处理记录</h2>
            <el-empty
              v-if="!comments.length"
              description="暂无留言"
              :image-size="65"
            /><el-timeline v-else
              ><el-timeline-item
                v-for="c in comments"
                :key="c.id"
                :timestamp="date(c.createdAt)"
                ><strong>{{ c.username }}</strong>
                <p class="pre-wrap">{{ c.content }}</p></el-timeline-item
              ></el-timeline
            ><el-input
              v-model="content"
              type="textarea"
              :rows="3"
              maxlength="2000"
              show-word-limit
              placeholder="补充信息或记录处理结果"
              aria-label="留言内容"
            />
            <div class="form-actions">
              <el-button type="primary" :loading="busy" @click="comment"
                >发送留言</el-button
              >
            </div>
          </section>
        </div>
        <div>
          <section class="panel">
            <h2>处理历史</h2>
            <el-empty
              v-if="!history.length"
              description="暂无历史记录"
              :image-size="60"
            /><el-timeline
              ><el-timeline-item
                v-for="h in history"
                :key="h.id"
                :timestamp="date(h.createdAt)"
                ><strong>{{ historyLabel(h) }}</strong>
                <p class="muted">{{ h.username }}</p></el-timeline-item
              ></el-timeline
            >
          </section>
          <section class="panel">
            <h2>可能对你有帮助</h2>
            <p class="muted">基于关键词匹配推荐</p>
            <el-empty
              v-if="!articles.length"
              description="暂无匹配的已发布知识"
              :image-size="60"
            /><router-link
              v-for="a in articles"
              :key="a.id"
              class="recommendation"
              :to="`/knowledge/${a.id}`"
              ><strong>{{ a.title }} →</strong>
              <p>{{ a.summary }}</p></router-link
            >
          </section>
        </div>
      </div>
    </template>
  </div>
</template>
