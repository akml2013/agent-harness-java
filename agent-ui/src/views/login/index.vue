<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="bg-shape shape-1"></div>
      <div class="bg-shape shape-2"></div>
      <div class="bg-shape shape-3"></div>
    </div>
    <div class="login-container">
      <div class="login-card">
        <div class="login-header">
          <el-icon :size="40" color="var(--accent-color)"><Monitor /></el-icon>
          <h1 class="login-title">AI Agent</h1>
          <p class="login-subtitle">企业智能办公助手</p>
        </div>
        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          class="login-form"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              placeholder="用户名"
              prefix-icon="User"
              size="large"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="密码"
              prefix-icon="Lock"
              size="large"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-form-item>
            <div class="login-options">
              <el-checkbox v-model="loginForm.remember">记住我</el-checkbox>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="login-btn"
              :loading="loginLoading"
              @click="handleLogin"
            >
              登录
            </el-button>
          </el-form-item>
        </el-form>
        <div class="login-footer">
          <span>暂无账号？请联系管理员开通</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from "@/stores/user";
import type { FormInstance } from "element-plus";
import { ElMessage } from "element-plus";
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";

const router = useRouter();
const userStore = useUserStore();

const loginFormRef = ref<FormInstance>();
const loginLoading = ref(false);

const loginForm = reactive({
  username: "admin",
  password: "admin123",
  remember: true,
});

const loginRules = {
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }],
};

async function handleLogin() {
  if (!loginFormRef.value) return;
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return;

    loginLoading.value = true;
    try {
      await new Promise((resolve) => setTimeout(resolve, 1000));
      userStore.setToken("mock-token-" + Date.now());
      userStore.setUserInfo({
        id: 1,
        username: loginForm.username,
        nickname: loginForm.username,
        avatar: "",
        email: loginForm.username + "@example.com",
        role: "user",
      });
      ElMessage.success("登录成功");
      router.push("/");
    } catch {
      ElMessage.error("登录失败，请检查用户名和密码");
    } finally {
      loginLoading.value = false;
    }
  });
}
</script>

<style scoped>
.login-page {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
  background: linear-gradient(
    135deg,
    var(--bg-login-gradient-start) 0%,
    var(--bg-login-gradient-mid) 50%,
    var(--bg-login-gradient-end) 100%
  );
}

.login-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.bg-shape {
  position: absolute;
  border-radius: 50%;
  opacity: 0.08;
}

.shape-1 {
  width: 600px;
  height: 600px;
  background: var(--bg-login-shape1);
  top: -200px;
  right: -100px;
}

.shape-2 {
  width: 400px;
  height: 400px;
  background: var(--bg-login-shape2);
  bottom: -100px;
  left: -100px;
}

.shape-3 {
  width: 300px;
  height: 300px;
  background: var(--bg-login-shape3);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.login-container {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  width: 400px;
  background: var(--bg-login-card);
  border-radius: 16px;
  padding: 40px;
  box-shadow: var(--shadow-login-card);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-login-title);
  margin: 12px 0 4px;
}

.login-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
}

.login-form {
  margin-top: 8px;
}

.login-options {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.login-btn {
  width: 100%;
}

.login-footer {
  text-align: center;
  margin-top: 16px;
  font-size: 13px;
  color: var(--text-secondary);
}
</style>
