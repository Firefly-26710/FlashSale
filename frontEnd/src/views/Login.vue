<template>
    <div class="login-container">
        <div class="ambient-shape shape-a"></div>
        <div class="ambient-shape shape-b"></div>
        <div class="login-box">
            <div class="login-header">
                <p class="caption">FLASH SALE</p>
                <h2>欢迎登录</h2>
            </div>
            <el-form :model="form" :rules="rules" ref="loginForm" class="login-form" @keyup.enter.native="onLogin">
                <el-form-item prop="username">
                    <el-input v-model="form.username" placeholder="请输入账号" size="large" />
                </el-form-item>
                <el-form-item prop="password">
                    <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large" show-password />
                </el-form-item>
                <el-form-item>
                    <el-button class="submit-btn" type="primary" @click="onLogin" size="large" :loading="loading">登录</el-button>
                </el-form-item>
            </el-form>
            <div class="switch-tip">
                <span>没有账号？</span>
                <el-button variant="text" @click="goRegister" class="switch-btn">立即注册</el-button>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api/auth'
import md5 from 'crypto-js/md5'

const router = useRouter()
const loading = ref(false)
const loginForm = ref(null)
const form = reactive({
    username: '',
    password: ''
})
const rules = {
    username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
    password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const onLogin = async () => {
    if (loading.value) return
    if (!form.username || !form.password) {
        ElMessage.error('请输入账号和密码')
        return
    }
    loading.value = true
    try {
        const res = await authApi.login(form.username, md5(form.password).toString())
        if (res.data && res.data.id) {
            ElMessage.success('登录成功')
            router.push('/')
        } else {
            ElMessage.error('登录失败')
        }
    } catch (err) {
        ElMessage.error(err?.response?.data?.message || '登录失败')
    } finally {
        loading.value = false
    }
}

const goRegister = () => {
    router.push('/register')
}
</script>

<style scoped>
.login-container {
    --brand-1: #0f8b8d;
    --brand-2: #f4a261;
    --ink-1: #1f2937;
    --ink-2: #4b5563;
    --line: #d8dee7;
    min-height: 100vh;
    width: 100vw;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
    position: relative;
    overflow: hidden;
    background:
        radial-gradient(circle at 16% 18%, rgba(15, 139, 141, 0.16), transparent 36%),
        radial-gradient(circle at 82% 78%, rgba(244, 162, 97, 0.24), transparent 38%),
        linear-gradient(135deg, #f7fafc 0%, #eef4ff 50%, #fff6eb 100%);
}

.ambient-shape {
    position: absolute;
    border-radius: 999px;
    filter: blur(8px);
    pointer-events: none;
}

.shape-a {
    width: 320px;
    height: 320px;
    top: -110px;
    right: -60px;
    background: rgba(15, 139, 141, 0.18);
}

.shape-b {
    width: 260px;
    height: 260px;
    bottom: -90px;
    left: -80px;
    background: rgba(244, 162, 97, 0.24);
}

.login-box {
    width: 100%;
    max-width: 420px;
    padding: 34px 30px 30px;
    border-radius: 20px;
    border: 1px solid rgba(255, 255, 255, 0.9);
    background: rgba(255, 255, 255, 0.78);
    backdrop-filter: blur(10px);
    box-shadow:
        0 24px 60px rgba(20, 40, 80, 0.14),
        0 8px 20px rgba(20, 40, 80, 0.08);
    position: relative;
    z-index: 2;
}
.switch-tip {
    text-align: center;
    margin-top: 18px;
    color: var(--ink-2);
    font-size: 15px;
}
.switch-btn {
    font-size: 15px;
    color: var(--brand-1);
    font-weight: bold;
    padding: 0 4px;
}

.login-header {
    text-align: center;
    margin-bottom: 26px;
}

.caption {
    font-family: "Segoe UI Variable", "Noto Sans SC", "Microsoft YaHei", sans-serif;
    font-size: 12px;
    letter-spacing: 2px;
    color: var(--ink-2);
    margin: 0 0 10px;
}

.login-header h2 {
    margin: 0;
    font-family: "Trebuchet MS", "Segoe UI Variable", "Noto Sans SC", sans-serif;
    font-size: 34px;
    line-height: 1.05;
    letter-spacing: 0.5px;
    color: var(--ink-1);
}

.login-form .el-form-item {
    margin-bottom: 18px;
}

.login-form :deep(.el-input__wrapper) {
    min-height: 46px;
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.94);
    box-shadow: 0 0 0 1px var(--line) inset;
    transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.login-form :deep(.el-input__wrapper:hover) {
    box-shadow: 0 0 0 1px rgba(15, 139, 141, 0.45) inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
    transform: translateY(-1px);
    box-shadow: 0 0 0 2px rgba(15, 139, 141, 0.65) inset !important;
}

.submit-btn {
    width: 100%;
    height: 46px;
    margin-top: 4px;
    border: 0;
    border-radius: 12px;
    font-family: "Segoe UI Variable", "Noto Sans SC", "Microsoft YaHei", sans-serif;
    font-size: 16px;
    font-weight: 700;
    letter-spacing: 1px;
    background: linear-gradient(92deg, var(--brand-1) 0%, var(--brand-2) 100%);
    box-shadow: 0 12px 20px rgba(15, 139, 141, 0.22);
    transition: transform 0.2s ease, box-shadow 0.2s ease, filter 0.2s ease;
}
.submit-btn:hover {
    transform: translateY(-2px);
    box-shadow: 0 16px 24px rgba(15, 139, 141, 0.26);
    filter: saturate(1.08);
}

@media (max-width: 560px) {
    .login-container {
        padding: 16px;
    }

    .login-box {
        padding: 28px 18px 22px;
        border-radius: 16px;
    }

    .login-header {
        margin-bottom: 20px;
    }

    .login-header h2 {
        font-size: 28px;
    }
}
</style>
