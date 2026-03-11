<template>
  <div class="home-page">
    <div class="ambient-shape shape-a"></div>
    <div class="ambient-shape shape-b"></div>

    <header class="topbar card-surface">
      <div class="brand-block">
        <p class="caption">FLASH SALE</p>
        <h1>秒杀系统首页</h1>
      </div>
      <div class="topbar-actions">
        <el-button text class="link-btn">个人中心</el-button>
        <el-button text class="link-btn">消息</el-button>
        <el-button type="primary" class="primary-btn" @click="logout">退出登录</el-button>
      </div>
    </header>

    <main class="content-wrap">
      <section class="hero card-surface">
        <p class="hero-tag">活动进行中</p>
        <h2>高并发秒杀活动主页框架</h2>
        <p class="hero-desc">
          这里预留了商品列表、活动倒计时、抢购入口、订单状态等模块的位置。
          当前仅搭建结构与视觉样式，后续可逐步填充业务逻辑。
        </p>
        <div class="hero-actions">
          <el-button type="primary" class="primary-btn">进入活动区</el-button>
          <el-button class="ghost-btn">查看活动规则</el-button>
        </div>
      </section>

      <section class="grid-area">
        <article class="panel card-surface">
          <h3>活动看板</h3>
          <p>预留：倒计时、在线人数、活动场次切换。</p>
        </article>
        <article class="panel card-surface">
          <h3>商品列表</h3>
          <p>预留：商品卡片、库存状态、价格信息。</p>
        </article>
        <article class="panel card-surface">
          <h3>我的订单</h3>
          <p>预留：待支付、进行中、已完成订单入口。</p>
        </article>
        <article class="panel card-surface">
          <h3>系统公告</h3>
          <p>预留：活动通知、风控提醒、帮助文档。</p>
        </article>
      </section>
    </main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/authStore'

const router = useRouter()
const authStore = useAuthStore()

const logout = () => {
  authStore.clearToken()
  router.replace('/login')
}
</script>

<style scoped>
.home-page {
  --brand-1: #0f8b8d;
  --brand-2: #f4a261;
  --ink-1: #1f2937;
  --ink-2: #4b5563;
  --line: #d8dee7;
  min-height: 100vh;
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

.card-surface {
  border: 1px solid rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(10px);
  box-shadow: 0 18px 38px rgba(20, 40, 80, 0.12);
  border-radius: 20px;
}

.topbar {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 22px;
  margin-bottom: 18px;
}

.caption {
  font-family: "Segoe UI Variable", "Noto Sans SC", "Microsoft YaHei", sans-serif;
  font-size: 12px;
  letter-spacing: 2px;
  color: var(--ink-2);
  margin: 0 0 8px;
}

.brand-block h1 {
  margin: 0;
  font-family: "Trebuchet MS", "Segoe UI Variable", "Noto Sans SC", sans-serif;
  font-size: 30px;
  color: var(--ink-1);
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.content-wrap {
  position: relative;
  z-index: 2;
  display: grid;
  gap: 16px;
}

.hero {
  padding: 24px;
}

.hero-tag {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 139, 141, 0.14);
  color: var(--brand-1);
  font-size: 13px;
  margin: 0 0 12px;
}

.hero h2 {
  margin: 0;
  color: var(--ink-1);
  font-size: 26px;
}

.hero-desc {
  margin: 12px 0 0;
  color: var(--ink-2);
  line-height: 1.7;
}

.hero-actions {
  margin-top: 18px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.primary-btn {
  border: 0;
  border-radius: 12px;
  background: linear-gradient(92deg, var(--brand-1) 0%, var(--brand-2) 100%);
}

.ghost-btn,
.link-btn {
  border-radius: 12px;
}

.grid-area {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.panel {
  padding: 20px;
}

.panel h3 {
  margin: 0;
  color: var(--ink-1);
  font-size: 20px;
}

.panel p {
  margin: 10px 0 0;
  color: var(--ink-2);
  line-height: 1.7;
}

@media (max-width: 900px) {
  .home-page {
    padding: 16px;
  }

  .topbar {
    flex-direction: column;
    align-items: flex-start;
    gap: 14px;
  }

  .grid-area {
    grid-template-columns: 1fr;
  }
}
</style>
