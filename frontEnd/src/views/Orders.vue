<template>
  <div class="orders-page">
    <header class="orders-header glass">
      <div class="title-wrap">
        <el-button class="back-btn" @click="goHome">返回首页</el-button>
        <h1>我的订单</h1>
      </div>
      <el-button type="primary" class="brand-btn" :loading="loadingOrders" @click="loadMyOrders">刷新订单</el-button>
    </header>

    <main class="orders-content">
      <section class="query-box glass">
        <div class="query-head">
          <h3>按订单号查询</h3>
          <p>可精确定位某一笔订单</p>
        </div>

        <div class="query-row">
          <el-input v-model="queryOrderId" placeholder="请输入订单号，例如：294726760354942976" clearable />
          <el-button type="primary" class="brand-btn" :loading="loadingOrderById" @click="queryOrderById">查询</el-button>
        </div>

        <div v-if="queriedOrder" class="order-card highlighted">
          <div class="highlighted-head">
            <h4>{{ getProductName(queriedOrder.productId) }}</h4>
            <el-tag :type="getStatusTagType(queriedOrder.status)" effect="light">{{ formatStatus(queriedOrder.status) }}</el-tag>
          </div>
          <div class="order-kv-grid">
            <p><span>订单号</span>{{ queriedOrder.id }}</p>
            <p><span>订单金额</span>￥{{ Number(queriedOrder.amount).toFixed(2) }}</p>
            <p><span>下单时间</span>{{ formatTime(queriedOrder.createdAt) }}</p>
          </div>
          <div class="query-actions">
            <el-button size="small" @click="goProductDetail(queriedOrder.productId)">查看商品</el-button>
            <el-button
              v-if="canPay(queriedOrder.status)"
              size="small"
              type="primary"
              class="brand-btn"
              :loading="payingOrderId === String(queriedOrder.id)"
              @click="payOrder(queriedOrder.id)"
            >
              模拟支付
            </el-button>
          </div>
        </div>
      </section>

      <section class="list-box glass">
        <div class="list-header">
          <h3>我的订单</h3>
          <p>共 {{ filteredOrders.length }} 条 / 总 {{ orders.length }} 条</p>
        </div>

        <div class="filter-row">
          <span class="filter-label">订单状态</span>
          <el-radio-group v-model="activeStatus" size="small">
            <el-radio-button
              v-for="option in statusOptions"
              :key="option.value"
              :label="option.value"
            >
              {{ option.label }}
              <span class="filter-count">
                {{ option.value === 'ALL' ? orders.length : statusCounts[option.value] || 0 }}
              </span>
            </el-radio-button>
          </el-radio-group>
        </div>

        <div v-if="loadingOrders" class="status-row">订单加载中...</div>
        <div v-else-if="orders.length === 0" class="status-row">暂无订单，快去秒杀商品吧</div>
        <div v-else-if="filteredOrders.length === 0" class="status-row">该状态下暂无订单</div>

        <div v-else class="order-list">
          <article class="order-card" v-for="item in filteredOrders" :key="item.id">
            <div class="order-top">
              <img class="product-cover" :src="getProductImage(item.productId)" :alt="getProductName(item.productId)" />
              <div class="order-main">
                <h4>{{ getProductName(item.productId) }}</h4>
                <p>订单号：{{ item.id }}</p>
                <p>订单状态：{{ formatStatus(item.status) }}</p>
                <p>订单金额：￥{{ Number(item.amount).toFixed(2) }}</p>
                <p>下单时间：{{ formatTime(item.createdAt) }}</p>
              </div>
            </div>
            <div class="order-actions">
              <el-button size="small" @click="goProductDetail(item.productId)">查看商品</el-button>
                <el-button
                v-if="canPay(item.status)"
                size="small"
                type="primary"
                class="brand-btn"
                :loading="payingOrderId === String(item.id)"
                @click="payOrder(item.id)"
              >
                模拟支付
              </el-button>
            </div>
          </article>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/authStore'
import { orderApi } from '../api/order'
import { productApi } from '../api/product'
import { authApi } from '../api/auth'

const router = useRouter()
const authStore = useAuthStore()

const orders = ref([])
const products = ref([])
const loadingOrders = ref(false)
const loadingOrderById = ref(false)
const queryOrderId = ref('')
const queriedOrder = ref(null)
const payingOrderId = ref('')
const activeStatus = ref('ALL')
const fallbackImage = 'https://picsum.photos/seed/order-product/400/260'

const statusOptions = [
  { label: '全部', value: 'ALL' },
  { label: '待支付', value: 'PENDING_PAYMENT' },
  { label: '已支付', value: 'PAID' },
  { label: '下单成功', value: 'SUCCESS' },
  { label: '支付失败', value: 'PAY_FAILED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '处理中', value: 'PENDING' },
]

const statusCounts = computed(() => {
  const counts = {}
  for (const item of orders.value) {
    const status = item?.status || 'UNKNOWN'
    counts[status] = (counts[status] || 0) + 1
  }
  return counts
})

const filteredOrders = computed(() => {
  if (activeStatus.value === 'ALL') {
    return orders.value
  }
  return orders.value.filter((item) => item.status === activeStatus.value)
})

// 从 token 同步用户ID，避免 localStorage 中 userId 缺失或过期导致“我的订单”为空。
const ensureUserId = async () => {
  if (authStore.userId) {
    return authStore.userId
  }

  if (!authStore.token) {
    return null
  }

  try {
    const { data } = await authApi.getUserInfo()
    if (data?.id) {
      authStore.setUserId(data.id)
      return data.id
    }
  } catch (error) {
    return null
  }

  return null
}

// 加载我的订单列表。
const loadMyOrders = async () => {
  const currentUserId = await ensureUserId()

  if (!currentUserId) {
    ElMessage.warning('请先登录')
    router.replace('/login')
    return
  }

  loadingOrders.value = true
  try {
    const { data } = await orderApi.getByUserId(currentUserId)
    orders.value = Array.isArray(data) ? data : []
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '订单加载失败')
    orders.value = []
  } finally {
    loadingOrders.value = false
  }
}

// 按订单号查询订单。
const queryOrderById = async () => {
  const orderId = queryOrderId.value.trim()
  if (!orderId) {
    ElMessage.warning('请输入订单号')
    return
  }

  loadingOrderById.value = true
  try {
    const { data } = await orderApi.getByOrderId(orderId)
    queriedOrder.value = data
  } catch (error) {
    queriedOrder.value = null
    ElMessage.error(error?.response?.data?.message || '订单查询失败')
  } finally {
    loadingOrderById.value = false
  }
}

// 加载商品信息，用于订单列表展示商品名和图片。
const loadProducts = async () => {
  try {
    const { data } = await productApi.list()
    products.value = Array.isArray(data) ? data : []
  } catch (error) {
    products.value = []
  }
}

const getProductName = (productId) => {
  const item = products.value.find((row) => String(row.id) === String(productId))
  return item?.name || `商品 #${productId}`
}

const getProductImage = (productId) => {
  const item = products.value.find((row) => String(row.id) === String(productId))
  return item?.imageUrl || fallbackImage
}

const formatStatus = (status) => {
  if (status === 'SUCCESS') return '下单成功'
  if (status === 'PENDING_PAYMENT') return '待支付'
  if (status === 'PAID') return '已支付'
  if (status === 'PAY_FAILED') return '支付失败'
  if (status === 'CANCELLED') return '已取消'
  if (status === 'PENDING') return '处理中'
  return '未知状态'
}

const getStatusTagType = (status) => {
  if (status === 'PAID') return 'success'
  if (status === 'PENDING_PAYMENT') return 'warning'
  if (status === 'PAY_FAILED') return 'danger'
  if (status === 'CANCELLED') return 'info'
  return 'info'
}

const canPay = (status) => status === 'PENDING_PAYMENT'

const payOrder = async (orderId) => {
  const id = String(orderId)
  payingOrderId.value = id
  try {
    await orderApi.pay(id)
    ElMessage.success('支付请求已受理，请稍后刷新查看结果')
    await Promise.all([loadMyOrders(), queryOrderId.value.trim() === id ? queryOrderById() : Promise.resolve()])
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '发起支付失败')
  } finally {
    payingOrderId.value = ''
  }
}

const formatTime = (time) => {
  if (!time) return '未知时间'
  const date = new Date(time)
  if (Number.isNaN(date.getTime())) return String(time)
  return date.toLocaleString('zh-CN', { hour12: false })
}

const goProductDetail = (productId) => {
  router.push(`/products/${productId}`)
}

const goHome = () => {
  router.push('/')
}

onMounted(async () => {
  await Promise.all([loadProducts(), loadMyOrders()])
})
</script>

<style scoped>
.orders-page {
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

.orders-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
}

.title-wrap h1 {
  margin: 0;
  color: var(--ink-1);
  font-size: 26px;
}

.back-btn {
  border-radius: 10px;
}

.brand-btn {
  border: 0;
  border-radius: 12px;
  background: linear-gradient(96deg, var(--brand-1) 0%, var(--brand-2) 100%);
}

.orders-content {
  margin-top: 16px;
  display: grid;
  gap: 16px;
}

.query-box,
.list-box {
  padding: 16px;
}

.query-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 10px;
}

.query-head p {
  margin: 0;
  color: var(--ink-2);
  font-size: 13px;
}

.query-box h3,
.list-box h3 {
  margin: 0;
  color: var(--ink-1);
}

.query-row {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.query-row :deep(.el-input) {
  flex: 1;
  min-width: 280px;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.list-header p {
  margin: 0;
  color: var(--ink-2);
}

.filter-row {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.filter-label {
  color: var(--ink-2);
  font-size: 13px;
}

.filter-count {
  margin-left: 6px;
  color: var(--ink-2);
  font-size: 12px;
}

.status-row {
  margin-top: 12px;
  color: var(--ink-2);
}

.order-list {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.order-card {
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 12px;
  padding: 12px;
}

.order-card.highlighted {
  margin-top: 12px;
  border-style: dashed;
  background: #fbfdff;
}

.highlighted-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.order-card h4 {
  margin: 0;
  color: var(--ink-1);
}

.order-card p {
  margin: 6px 0 0;
  color: var(--ink-2);
}

.order-top {
  display: flex;
  gap: 10px;
}

.product-cover {
  width: 120px;
  height: 84px;
  border-radius: 10px;
  object-fit: cover;
}

.order-main {
  flex: 1;
}

.order-kv-grid {
  margin-top: 8px;
  display: grid;
  gap: 6px;
}

.order-kv-grid p {
  margin: 0;
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.order-kv-grid span {
  color: var(--ink-2);
}

.query-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}

.order-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .orders-page {
    padding: 16px;
  }

  .orders-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .order-list {
    grid-template-columns: 1fr;
  }

  .order-top {
    flex-direction: column;
  }

  .product-cover {
    width: 100%;
    height: 180px;
  }
}
</style>
