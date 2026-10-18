<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import api from "../api";
import { useAuth } from "../stores/auth";
const auth = useAuth(),
  router = useRouter(),
  busy = ref(false),
  form = reactive({ oldPassword: "", newPassword: "", confirm: "" });
async function submit() {
  if (!form.oldPassword || form.newPassword.length < 8)
    return ElMessage.warning("请填写原密码，新密码至少8位");
  if (form.newPassword !== form.confirm)
    return ElMessage.warning("两次新密码不一致");
  busy.value = true;
  try {
    await api.post("/users/change-password", {
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    });
    auth.clear();
    ElMessage.success("密码已更新，请重新登录");
    router.replace("/login");
  } catch {
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section class="panel form-panel">
    <h2>账号安全</h2>
    <p>{{ auth.username }} · {{ auth.isAdmin ? "管理员" : "普通用户" }}</p>
    <p class="muted">修改密码后，当前账号全部旧会话立即失效。</p>
    <el-form label-position="top" @submit.prevent="submit"
      ><el-form-item label="原密码"
        ><el-input
          v-model="form.oldPassword"
          type="password"
          show-password
          autocomplete="current-password" /></el-form-item
      ><el-form-item label="新密码"
        ><el-input
          v-model="form.newPassword"
          type="password"
          show-password
          autocomplete="new-password"
          maxlength="72" /></el-form-item
      ><el-form-item label="确认新密码"
        ><el-input
          v-model="form.confirm"
          type="password"
          show-password
          autocomplete="new-password" /></el-form-item
      ><el-button type="primary" native-type="submit" :loading="busy"
        >更新密码</el-button
      ></el-form
    >
  </section>
</template>
