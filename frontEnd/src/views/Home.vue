<template>
  <div class="home-page">
    <header class="main-header glass">
      <div class="logo-wrap">
        <p class="logo-caption">FLASH SALE</p>
        <h1>极光秒杀商城</h1>
      </div>
      <div class="header-search">
        <el-input
          v-model="keyword"
          placeholder="搜索商品 / 品牌 / 活动"
          size="large"
          clearable
        />
      </div>
      <div class="header-actions">
        <el-button text>个人中心</el-button>
        <el-button text @click="goOrders">我的订单</el-button>
        <el-button type="primary" class="brand-btn" @click="logout">退出登录</el-button>
      </div>
    </header>

    <main class="page-body">
      <section class="hero-banner glass">
        <div class="hero-left">
          <span class="hero-badge">限时抢购 · 进行中</span>
          <h2>爆款低至 1 折，整点开抢</h2>
          <p>
            覆盖数码家电、美妆日化、运动户外等热门品类，实时库存刷新，支持高并发下的稳定抢购。
          </p>
          <div class="hero-cta">
            <el-button type="primary" class="brand-btn" size="large">立即抢购</el-button>
            <el-button size="large">查看活动规则</el-button>
          </div>
        </div>
        <div class="hero-stats">
          <div class="stat-card">
            <p>今日活动场次</p>
            <strong>08 场</strong>
          </div>
          <div class="stat-card">
            <p>在线参与人数</p>
            <strong>12.8 万</strong>
          </div>
          <div class="stat-card">
            <p>爆款商品数量</p>
            <strong>{{ products.length }}</strong>
          </div>
        </div>
      </section>

      <section class="category-row">
        <article v-for="name in categories" :key="name" class="category-item glass">{{ name }}</article>
      </section>

      <section class="content-grid">
        <div class="product-area glass">
          <div class="section-head">
            <h3>热门秒杀商品</h3>
            <el-button text>查看全部</el-button>
          </div>

          <div v-if="loadingProducts || searchingProducts" class="status-row">商品加载中...</div>
          <div v-else-if="displayedProducts.length === 0" class="status-row">暂无匹配商品</div>

          <div v-else class="product-grid">
            <article v-for="item in displayedProducts" :key="item.id" class="product-card">
              <img class="product-cover" :src="item.imageUrl || fallbackImage" :alt="item.name" />
              <div class="product-info">
                <h4>{{ item.name }}</h4>
                <p class="desc">{{ item.description || '精选爆款，限时优惠，支持快速下单。' }}</p>
                <div class="meta-row">
                  <p class="price">￥{{ Number(item.price).toFixed(2) }}</p>
                  <p class="stock">库存 {{ item.stock }}</p>
                </div>
                <el-button class="full-btn" type="primary" @click="goProductDetail(item.id)">查看详情</el-button>
              </div>
            </article>
          </div>
        </div>

        <aside class="side-area">
          <section class="side-card glass">
            <h3>我的订单</h3>
            <p>在独立订单页面查看全部订单与订单详情。</p>
            <el-button text @click="goOrders">进入我的订单</el-button>
          </section>
          <section class="side-card glass">
            <h3>系统公告</h3>
            <p>今晚 20:00 开启超级秒杀专场，建议提前登录并完善收货信息。</p>
          </section>
          <section class="side-card glass">
            <h3>服务保障</h3>
            <ul>
              <li>官方正品保障</li>
              <li>7 天无忧退换</li>
              <li>平台风控护航</li>
            </ul>
          </section>
        </aside>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/authStore'
import { productApi } from '../api/product'

const router = useRouter()
const authStore = useAuthStore()
const products = ref([])
const loadingProducts = ref(false)
const searchingProducts = ref(false)
const searchResults = ref([])
const keyword = ref('')
const fallbackImage = 'https://picsum.photos/seed/flashsale/640/360'
const categories = ['手机数码', '家用电器', '美妆个护', '运动户外', '食品生鲜', '母婴玩具']
let searchTimer = null

const displayedProducts = computed(() => {
  if (!keyword.value.trim()) {
    return products.value
  }
  return searchResults.value
})

const loadProducts = async () => {
  loadingProducts.value = true
  try {
    const { data } = await productApi.list()
    products.value = Array.isArray(data) ? data : []
  } catch (error) {
    products.value = []
  } finally {
    loadingProducts.value = false
  }
}

const doRemoteSearch = async (value) => {
  const q = value.trim()
  if (!q) {
    searchResults.value = []
    searchingProducts.value = false
    return
  }

  searchingProducts.value = true
  try {
    const { data } = await productApi.search(q, 20)
    searchResults.value = Array.isArray(data) ? data : []
  } catch (error) {
    searchResults.value = []
  } finally {
    searchingProducts.value = false
  }
}

watch(keyword, (value) => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }

  if (!value.trim()) {
    searchResults.value = []
    searchingProducts.value = false
    return
  }

  searchTimer = setTimeout(() => {
    doRemoteSearch(value)
  }, 300)
})

const goProductDetail = (id) => {
  router.push(`/products/${id}`)
}

const goOrders = () => {
  router.push('/orders')
}

const logout = () => {
  authStore.clearToken()
  router.replace('/login')
}

onMounted(loadProducts)

onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
})
</script>

<style scoped>
.home-page {
  --brand-1: #2563eb;
  --brand-2: #7c3aed;
  --ink-1: #0f172a;
  --ink-2: #475569;
  --line: #dbe4f3;
  min-height: 100vh;
  padding: 20px;
  background:
    radial-gradient(circle at 12% 12%, rgba(37, 99, 235, 0.2), transparent 32%),
    radial-gradient(circle at 88% 80%, rgba(124, 58, 237, 0.18), transparent 36%),
    linear-gradient(145deg, #f8fbff 0%, #eef3ff 48%, #f7f2ff 100%);
}

.glass {
  border: 1px solid rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(12px);
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.1);
  border-radius: 18px;
}

.main-header {
  display: grid;
  grid-template-columns: 260px minmax(280px, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
}

.logo-caption {
  margin: 0;
  font-size: 12px;
  letter-spacing: 2px;
  color: var(--ink-2);
}

.logo-wrap h1 {
  margin: 6px 0 0;
  font-size: 26px;
  color: var(--ink-1);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.brand-btn {
  border: 0;
  border-radius: 12px;
  background: linear-gradient(96deg, var(--brand-1) 0%, var(--brand-2) 100%);
}

.page-body {
  margin-top: 16px;
  display: grid;
  gap: 16px;
}

.hero-banner {
  padding: 24px;
  display: flex;
  justify-content: space-between;
  gap: 18px;
}

.hero-left {
  max-width: 700px;
}

.hero-badge {
  display: inline-block;
  background: rgba(37, 99, 235, 0.12);
  color: #1d4ed8;
  border-radius: 999px;
  padding: 5px 12px;
  font-size: 13px;
}

.hero-left h2 {
  margin: 12px 0 0;
  font-size: 34px;
  color: var(--ink-1);
}

.hero-left p {
  margin: 12px 0 0;
  color: var(--ink-2);
  line-height: 1.7;
}

.hero-cta {
  margin-top: 18px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.hero-stats {
  width: 260px;
  display: grid;
  gap: 10px;
}

.stat-card {
  background: #ffffff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 12px;
}

.stat-card p {
  margin: 0;
  color: var(--ink-2);
  font-size: 13px;
}

.stat-card strong {
  display: block;
  margin-top: 8px;
  color: var(--ink-1);
  font-size: 25px;
}

.category-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.category-item {
  padding: 10px 14px;
  color: var(--ink-1);
  font-weight: 600;
  border-radius: 12px;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 290px;
  gap: 16px;
}

.product-area {
  padding: 18px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-head h3 {
  margin: 0;
  color: var(--ink-1);
  font-size: 22px;
}

.status-row {
  margin-top: 14px;
  color: var(--ink-2);
}

.product-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.product-card {
  border: 1px solid var(--line);
  border-radius: 14px;
  overflow: hidden;
  background: #ffffff;
  display: flex;
  flex-direction: column;
}

.product-cover {
  width: 100%;
  aspect-ratio: 16 / 10;
  object-fit: cover;
}

.product-info {
  padding: 14px;
  display: grid;
  gap: 10px;
}

.product-info h4 {
  margin: 0;
  color: var(--ink-1);
  font-size: 18px;
}

.desc {
  margin: 0;
  color: var(--ink-2);
  line-height: 1.5;
  min-height: 42px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.price {
  margin: 0;
  color: #dc2626;
  font-size: 24px;
  font-weight: 700;
}

.stock {
  margin: 0;
  color: var(--ink-2);
  font-size: 13px;
}

.full-btn {
  width: 100%;
  border-radius: 10px;
}

.side-area {
  display: grid;
  gap: 12px;
  align-content: start;
}

.side-card {
  padding: 16px;
}

.side-card h3 {
  margin: 0;
  color: var(--ink-1);
}

.side-card p,
.side-card ul {
  margin: 10px 0 0;
  color: var(--ink-2);
  line-height: 1.7;
  padding-left: 18px;
}

.side-card li {
  margin: 4px 0;
}

@media (max-width: 900px) {
  .home-page {
    padding: 16px;
  }

  .main-header {
    grid-template-columns: 1fr;
  }

  .hero-banner {
    flex-direction: column;
  }

  .hero-stats {
    width: 100%;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .content-grid {
    grid-template-columns: 1fr;
  }

  .product-grid {
    grid-template-columns: 1fr;
  }
}
</style>