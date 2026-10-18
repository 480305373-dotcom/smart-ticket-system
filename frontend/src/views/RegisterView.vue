<script setup>
import { reactive, ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import api from "../api";
const router = useRouter(),
  busy = ref(false),
  form = reactive({ username: "", password: "", confirm: "" });
const registrationEnabled = ref(false), ready = ref(false);
onMounted(async () => {
  try {
    registrationEnabled.value = (await api.get("/system/public-config")).registrationEnabled;
    ready.value = true;
  } catch {}
});
async function submit() {
  if (!registrationEnabled.value) return;
  if (!form.username.trim() || form.password.length < 8)
    return ElMessage.warning("请填写用户名，密码至少8位");
  if (form.password !== form.confirm)
    return ElMessage.warning("两次密码不一致");
  busy.value = true;
  try {
    await api.post("/users/register", {
      username: form.username.trim(),
      password: form.password,
    });
    ElMessage.success("注册成功，请登录");
    router.replace("/login");
  } catch {
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <main class="registration">
    <section class="panel form-panel">
      <span class="eyebrow">SMART TICKET</span>
      <h1>注册服务账号</h1>
      <p class="muted">新账号默认仅能提交和跟进自己的工单。</p>
      <el-alert v-if="!ready" title="正在读取注册设置，请稍候或刷新重试" type="info" :closable="false" />
      <template v-else-if="!registrationEnabled">
        <el-alert title="当前不开放自行注册，请联系管理员创建账号。" type="info" :closable="false" />
        <p><router-link to="/login">返回登录</router-link></p>
      </template>
      <el-form v-else label-position="top" @submit.prevent="submit"
        ><el-form-item label="用户名"
          ><el-input
            v-model="form.username"
            maxlength="50"
            autocomplete="username" /></el-form-item
        ><el-form-item label="密码"
          ><el-input
            v-model="form.password"
            type="password"
            maxlength="72"
            show-password
            autocomplete="new-password" /></el-form-item
        ><el-form-item label="确认密码"
          ><el-input
            v-model="form.confirm"
            type="password"
            maxlength="72"
            show-password
            autocomplete="new-password" /></el-form-item
        ><el-button type="primary" native-type="submit" :loading="busy"
          >注册</el-button
        ><el-button @click="router.push('/login')">返回登录</el-button></el-form
      >
    </section>
  </main>
</template>
<style scoped>
.registration {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
}
.form-panel {
  width: min(100%, 480px);
}
h1 {
  margin: 12px 0 20px;
}
</style>
