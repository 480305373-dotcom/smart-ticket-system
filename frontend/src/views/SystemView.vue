<script setup>
import { ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import api from "../api";
import { date } from "../utils";
const data = ref(null),
  busy = ref(false),
  index = ref(null);
async function load() {
  busy.value = true;
  try {
    data.value = await api.get("/admin/system");
    if (data.value.aiEnabled) index.value = await api.get("/admin/ai/index");
  } catch {
  } finally {
    busy.value = false;
  }
}
async function retry(id) {
  try {
    await api.post(`/admin/outbox/${id}/retry`);
    ElMessage.success("已加入重试队列");
    await load();
  } catch {}
}
async function replay() {
  try {
    await ElMessageBox.confirm(
      "将一条死信安全加入待投递队列，确认继续？",
      "重放死信",
    );
    await api.post("/admin/mq/replay-dead-letter");
    ElMessage.success("已加入重试队列");
    await load();
  } catch {}
}
async function sync() {
  busy.value = true;
  try {
    index.value = await api.post("/admin/ai/index", {}, { timeout: 180000 });
    ElMessage.success("知识索引同步完成");
  } catch {
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div v-loading="busy">
    <section class="panel">
      <div class="panel-title">
        <h2>服务与消息监控</h2>
        <el-button @click="load">刷新状态</el-button>
      </div>
      <p class="muted">
        展示真实依赖状态与数据库事件；重试不会重复产生消费审计。
      </p>
      <el-alert
        v-if="!data"
        title="暂时无法读取运行状态，请刷新重试"
        type="warning"
        :closable="false"
      />
      <template v-else>
        <el-descriptions :column="3" border>
          <el-descriptions-item
            v-for="name in ['postgres', 'redis', 'rabbitmq']"
            :key="name"
            :label="name"
            ><el-tag
              :type="
                data[name] === 'UP'
                  ? 'success'
                  : data[name] === 'DISABLED'
                    ? 'info'
                    : 'danger'
              "
              >{{ data[name] }}</el-tag
            ></el-descriptions-item
          >
          <el-descriptions-item label="待消费消息">{{
            data.queueDepth
          }}</el-descriptions-item
          ><el-descriptions-item label="死信消息">{{
            data.deadLetters
          }}</el-descriptions-item
          ><el-descriptions-item label="AI 模块">{{
            data.aiEnabled ? "已启用" : "未启用"
          }}</el-descriptions-item>
        </el-descriptions>
        <p>
          <el-button
            :disabled="data.rabbitmq !== 'UP' || !data.deadLetters"
            @click="replay"
            >重放一条死信</el-button
          ><el-button v-if="data.aiEnabled" @click="sync"
            >同步知识向量索引</el-button
          >
        </p>
        <p v-if="index" class="muted">
          知识索引：{{ index.message }}，本轮更新 {{ index.indexed }} 篇。{{
            index.lastSync ? date(index.lastSync) : ""
          }}
        </p>
        <p class="muted">
          PENDING：等待投递；SENT：Broker
          已确认；FAILED：达到自动重试上限，可人工恢复。
        </p>
      </template>
    </section>
    <section v-if="data" class="panel">
      <h2>最近事件</h2>
      <el-table
        :data="data.events"
        empty-text="暂无异步事件；轻量模式直接写入审计"
      >
        <el-table-column
          prop="event_id"
          label="事件编号"
          min-width="290"
        /><el-table-column
          prop="status"
          label="状态"
          width="110"
        /><el-table-column
          prop="attempts"
          label="失败次数"
          width="100"
        /><el-table-column
          prop="last_error"
          label="最近错误类型"
          min-width="160"
        />
        <el-table-column label="创建时间" width="180"
          ><template #default="{ row }">{{
            date(row.created_at)
          }}</template></el-table-column
        ><el-table-column label="操作" width="90"
          ><template #default="{ row }"
            ><el-button
              v-if="row.status === 'FAILED'"
              link
              type="primary"
              @click="retry(row.event_id)"
              >重试</el-button
            ></template
          ></el-table-column
        >
      </el-table>
    </section>
  </div>
</template>
