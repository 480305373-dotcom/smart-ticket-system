<script setup>
import { reactive, ref, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import api from "../api";
import { priorityLabel } from "../utils";
const route = useRoute(),
  router = useRouter(),
  editing = !!route.params.id,
  formRef = ref(),
  busy = ref(false),
  ready = ref(!editing);
const form = reactive({ title: "", description: "", priority: 3 });
let lastPayload = "",
  requestId = "";
const rules = {
  title: [
    { required: true, message: "标题不能为空", trigger: "blur" },
    { max: 200, message: "标题不能超过200字", trigger: "blur" },
  ],
  description: [{ max: 1000, message: "描述不能超过1000字", trigger: "blur" }],
  priority: [
    {
      required: true,
      type: "number",
      min: 1,
      max: 5,
      message: "优先级必须在1到5之间",
      trigger: "change",
    },
  ],
};
onMounted(async () => {
  if (editing)
    try {
      const t = await api.get(`/tickets/${route.params.id}`);
      Object.assign(form, {
        title: t.title,
        description: t.description,
        priority: t.priority,
      });
      ready.value = true;
    } catch {}
});
async function submit() {
  if (busy.value) return;
  if (!(await formRef.value.validate().catch(() => false))) return;
  const payload = JSON.stringify(form);
  if (payload !== lastPayload) {
    requestId = crypto.randomUUID();
    lastPayload = payload;
  }
  busy.value = true;
  try {
    const t = editing
      ? await api.put(`/tickets/${route.params.id}`, form)
      : await api.post("/tickets", form, {
          headers: { "X-Request-Id": requestId },
        });
    ElMessage.success(editing ? "工单修改成功" : "工单创建成功");
    router.push(`/tickets/${t.id}`);
  } catch {
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section class="panel form-panel">
    <div class="panel-title">
      <h2>{{ editing ? "更新工单信息" : "提交服务需求" }}</h2>
      <el-button text @click="$router.back()">返回</el-button>
    </div>
    <p class="muted">
      说明问题现象、发生时间和影响范围，帮助服务团队快速定位。
    </p>
    <el-alert
      v-if="!ready"
      title="无法加载工单，请检查权限或返回列表"
      type="error"
      :closable="false"
    /><el-form
      v-else
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="submit"
      ><el-form-item label="标题" prop="title"
        ><el-input
          v-model="form.title"
          maxlength="200"
          show-word-limit
          placeholder="例如：VPN提示认证失败，无法连接公司网络" /></el-form-item
      ><el-form-item label="问题描述" prop="description"
        ><el-input
          v-model="form.description"
          type="textarea"
          :rows="7"
          maxlength="1000"
          show-word-limit
          placeholder="请填写具体故障现象与已尝试的操作，勿填写密码和验证码" /></el-form-item
      ><el-form-item label="优先级" prop="priority"
        ><el-radio-group v-model="form.priority"
          ><el-radio-button v-for="n in 5" :key="n" :value="n">{{
            priorityLabel(n)
          }}</el-radio-button></el-radio-group
        ></el-form-item
      >
      <div class="form-actions">
        <el-button @click="$router.push('/tickets')">取消</el-button
        ><el-button type="primary" native-type="submit" :loading="busy">{{
          editing ? "保存修改" : "提交工单"
        }}</el-button>
      </div></el-form
    >
  </section>
</template>
