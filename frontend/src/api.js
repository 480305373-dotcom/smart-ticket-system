import axios from "axios";
import { ElMessage } from "element-plus";
import { useAuth } from "./stores/auth";
import router from "./router";
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  timeout: 15000,
});
api.interceptors.request.use((config) => {
  const token = useAuth().token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
api.interceptors.response.use(
  (response) => {
    if (response.data.code !== 200) {
      const error = new Error(response.data.message || "请求失败");
      ElMessage.error(error.message);
      return Promise.reject(error);
    }
    return response.data.data;
  },
  (error) => {
    const status = error.response?.status;
    if (status === 401 && !error.config?.url?.includes("/users/login")) {
      useAuth().clear();
      router.replace("/login");
    }
    error.userMessage =
      error.response?.data?.message ||
      (status === 403
        ? "没有权限执行此操作。"
        : "服务连接失败，请检查后端是否启动。");
    ElMessage.error(error.userMessage);
    return Promise.reject(error);
  },
);
export default api;
