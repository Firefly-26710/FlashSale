<template>
  <div class="detail-page">
    <header class="detail-header glass">
      <div class="left-block">
        <el-button class="back-btn" @click="goBack">返回首页</el-button>
        <h1>商品详情</h1>
      </div>
      <el-button text>分享</el-button>
    </header>

    <main class="detail-content">
      <section v-if="loading" class="glass status-box">正在加载商品...</section>
      <section v-else-if="error" class="glass status-box error">{{ error }}</section>

      <section v-else-if="product" class="layout-grid">
        <article class="gallery glass">
          <img class="main-cover" :src="product.imageUrl || fallbackImage" alt="商品图片" />
          <div class="thumb-row">
            <img class="thumb active" :src="product.imageUrl || fallbackImage" alt="缩略图" />
            <img class="thumb" :src="fallbackAltA" alt="缩略图" />
            <img class="thumb" :src="fallbackAltB" alt="缩略图" />
          </div>
        </article>

        <article class="info-card glass">
          <p class="tag">官方直售</p>
          <h2>{{ product.name }}</h2>
          <p class="desc">{{ product.description }}</p>

          <div class="price-box">
            <div>
              <p class="label">秒杀价</p>
              <p class="price">￥{{ Number(product.price).toFixed(2) }}</p>
            </div>
            <div class="stock-wrap">
              <p class="label">库存状态</p>
              <p class="stock" :class="{ warn: product.stock < 20 }">
                {{ product.stock > 0 ? `剩余 ${product.stock} 件` : '已售罄' }}
              </p>
            </div>
          </div>

          <div class="service-row">
            <span>正品保障</span>
            <span>极速发货</span>
            <span>7天无忧退</span>
            <span>平台风控保障</span>
          </div>

          <div class="purchase-row">
            <el-button
              class="buy-btn"
              type="primary"
              size="large"
              :loading="submittingOrder"
              :disabled="product.stock === 0"
              @click="handleSeckill"
            >
              立即秒杀
            </el-button>
            <el-button size="large">加入收藏</el-button>
          </div>

          <div class="tips-row">
            <p>活动说明：下单后 15 分钟内完成支付，超时订单将自动取消。</p>
            <p>配送说明：工作日下单后预计 24 小时内发货。</p>
          </div>

          <p class="seckill-feedback" v-if="orderMessage">{{ orderMessage }}</p>
        </article>

        <article class="detail-tabs glass">
          <h3>商品详情说明</h3>
          <p>
            该商品属于平台精选秒杀活动，支持高并发下单与库存实时扣减。请在抢购前确认收货地址与支付方式，
            以获得更流畅的下单体验。
          </p>
          <ul>
            <li>支持移动端与 PC 端同步下单</li>
            <li>订单状态实时更新，可在首页查看</li>
            <li>异常下单会触发平台风控机制保障公平性</li>
          </ul>
        </article>

        <article class="recommend glass">
          <h3>你可能还喜欢</h3>
          <div class="recommend-list">
            <div class="recommend-item" v-for="name in recommendations" :key="name">
              <img :src="fallbackImage" alt="推荐商品" />
              <p>{{ name }}</p>
            </div>
          </div>
        </article>
      </section>

      <section v-else class="glass status-box">
        商品不存在或已下架。
      </section>
    </main>

    <el-dialog
      v-model="paymentDialogVisible"
      width="420px"
      :close-on-click-modal="false"
      :show-close="!processingPayment"
      class="payment-dialog"
      title="订单支付"
    >
      <div class="pay-modal-content">
        <p>订单号：{{ paymentOrderId || '-' }}</p>
        <p>商品：{{ product?.name || '-' }}</p>
        <p>金额：￥{{ Number(product?.price || 0).toFixed(2) }}</p>
        <p>状态：{{ paymentStatusText }}</p>
      </div>
      <template #footer>
        <div class="pay-modal-actions">
          <el-button
            :disabled="processingPayment || paymentStatus !== 'PENDING_PAYMENT'"
            @click="cancelCurrentOrder"
          >
            取消订单
          </el-button>
          <el-button
            type="primary"
            class="buy-btn"
            :loading="processingPayment"
            :disabled="paymentStatus !== 'PENDING_PAYMENT'"
            @click="payCurrentOrder"
          >
            立即支付
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { productApi } from '../api/product'
import { orderApi } from '../api/order'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const product = ref(null)
const submittingOrder = ref(false)
const orderMessage = ref('')
const paymentDialogVisible = ref(false)
const processingPayment = ref(false)
const paymentOrderId = ref('')
const paymentStatus = ref('PENDING_PAYMENT')
const fallbackImage = 'https://picsum.photos/seed/product-main/960/640'
const fallbackAltA = 'https://picsum.photos/seed/product-alt-a/320/220'
const fallbackAltB = 'https://picsum.photos/seed/product-alt-b/320/220'
const recommendations = ['电竞机械键盘', '旗舰降噪耳机', '便携咖啡机', '超轻运动跑鞋']

const paymentStatusText = computed(() => {
  if (paymentStatus.value === 'PENDING_PAYMENT') return '待支付'
  if (paymentStatus.value === 'PAID') return '支付成功'
  if (paymentStatus.value === 'PAY_FAILED') return '支付失败'
  if (paymentStatus.value === 'CANCELLED') return '已取消'
  return '处理中'
})

const loadDetail = async () => {
  loading.value = true
  error.value = ''
  try {
    const { data } = await productApi.detail(route.params.id)
    product.value = data
  } catch (err) {
    error.value = err?.response?.data?.message || '加载商品失败'
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.push('/')
}

// 秒杀后短轮询库存，兼容异步下单带来的最终一致性延迟。
const refreshStockWithRetry = async (originStock) => {
  for (let index = 0; index < 3; index += 1) {
    await new Promise((resolve) => setTimeout(resolve, 1000))
    await loadDetail()
    if (product.value?.stock < originStock) {
      return
    }
  }
}

const handleSeckill = async () => {
  if (!product.value?.id) return

  const originStock = Number(product.value.stock || 0)
  submittingOrder.value = true
  try {
    const { data } = await orderApi.seckill(product.value.id)
    paymentOrderId.value = String(data?.orderId || '')
    paymentStatus.value = 'PENDING_PAYMENT'
    paymentDialogVisible.value = true
    orderMessage.value = `${data?.message || '秒杀请求成功'}。订单号：${paymentOrderId.value || '-'}。`
    ElMessage.success('秒杀成功，请在支付窗口中选择支付或取消')
    await refreshStockWithRetry(originStock)
  } catch (err) {
    const msg = err?.response?.data?.message || '秒杀失败'
    orderMessage.value = msg
    ElMessage.error(msg)
  } finally {
    submittingOrder.value = false
  }
}

const refreshCurrentOrderStatus = async () => {
  if (!paymentOrderId.value) return
  const { data } = await orderApi.getByOrderId(paymentOrderId.value)
  paymentStatus.value = data?.status || paymentStatus.value
}

const payCurrentOrder = async () => {
  if (!paymentOrderId.value) return
  processingPayment.value = true
  try {
    await orderApi.pay(paymentOrderId.value)
    ElMessage.success('支付请求已发送，正在确认结果')

    for (let index = 0; index < 6; index += 1) {
      await new Promise((resolve) => setTimeout(resolve, 700))
      await refreshCurrentOrderStatus()
      if (paymentStatus.value === 'PAID' || paymentStatus.value === 'PAY_FAILED' || paymentStatus.value === 'CANCELLED') {
        break
      }
    }

    if (paymentStatus.value === 'PAID') {
      ElMessage.success('支付成功')
      orderMessage.value = `订单 ${paymentOrderId.value} 支付成功。`
      paymentDialogVisible.value = false
    } else if (paymentStatus.value === 'PAY_FAILED') {
      ElMessage.error('支付失败，请重试')
      orderMessage.value = `订单 ${paymentOrderId.value} 支付失败。`
    } else if (paymentStatus.value === 'CANCELLED') {
      ElMessage.warning('订单已取消')
      orderMessage.value = `订单 ${paymentOrderId.value} 已取消。`
      paymentDialogVisible.value = false
    } else {
      ElMessage.info('支付结果处理中，可在我的订单中查看')
      orderMessage.value = `订单 ${paymentOrderId.value} 支付处理中。`
    }
  } catch (err) {
    ElMessage.error(err?.response?.data?.message || '发起支付失败')
  } finally {
    processingPayment.value = false
  }
}

const cancelCurrentOrder = async () => {
  if (!paymentOrderId.value) return
  processingPayment.value = true
  try {
    await orderApi.cancel(paymentOrderId.value)
    paymentStatus.value = 'CANCELLED'
    paymentDialogVisible.value = false
    orderMessage.value = `订单 ${paymentOrderId.value} 已取消，库存已回补。`
    ElMessage.success('订单已取消')
    await loadDetail()
  } catch (err) {
    ElMessage.error(err?.response?.data?.message || '取消订单失败')
  } finally {
    processingPayment.value = false
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.detail-page {
  --brand-1: #2563eb;
  --brand-2: #7c3aed;
  --ink-1: #0f172a;
  --ink-2: #475569;
  --line: #dbe4f3;
  min-height: 100vh;
  padding: 20px;
  background:
    radial-gradient(circle at 10% 10%, rgba(37, 99, 235, 0.2), transparent 30%),
    radial-gradient(circle at 90% 80%, rgba(124, 58, 237, 0.18), transparent 35%),
    linear-gradient(150deg, #f8fbff 0%, #edf3ff 48%, #f7f1ff 100%);
}

.glass {
  border: 1px solid rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(12px);
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.1);
  border-radius: 18px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
}

.left-block {
  display: flex;
  align-items: center;
  gap: 12px;
}

.left-block h1 {
  margin: 0;
  color: var(--ink-1);
  font-size: 26px;
}

.back-btn {
  border-radius: 10px;
}

.detail-content {
  margin-top: 16px;
}

.status-box {
  padding: 18px;
  color: var(--ink-2);
}

.status-box.error {
  color: #dc2626;
}

.layout-grid {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(360px, 1fr);
  gap: 16px;
}

.gallery,
.info-card,
.detail-tabs,
.recommend {
  padding: 18px;
}

.main-cover {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  border-radius: 12px;
}

.thumb-row {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.thumb {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  border-radius: 10px;
  border: 2px solid transparent;
}

.thumb.active {
  border-color: var(--brand-1);
}

.tag {
  margin: 0;
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: #1d4ed8;
  background: rgba(37, 99, 235, 0.12);
}

.info-card h2 {
  margin: 12px 0 0;
  color: var(--ink-1);
  font-size: 30px;
}

.desc {
  margin: 10px 0 0;
  color: var(--ink-2);
  line-height: 1.75;
}

.price-box {
  margin-top: 14px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #ffffff;
  padding: 14px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.label {
  margin: 0;
  color: var(--ink-2);
  font-size: 13px;
}

.price {
  margin: 8px 0 0;
  color: #dc2626;
  font-size: 34px;
  font-weight: 700;
}

.stock {
  margin: 8px 0 0;
  color: #16a34a;
  font-size: 18px;
  font-weight: 600;
}

.stock.warn {
  color: #ea580c;
}

.service-row {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.service-row span {
  font-size: 13px;
  color: var(--ink-2);
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.85);
}

.purchase-row {
  margin-top: 16px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.buy-btn {
  border: 0;
  border-radius: 12px;
  background: linear-gradient(96deg, var(--brand-1) 0%, var(--brand-2) 100%);
}

.tips-row {
  margin-top: 14px;
}

.tips-row p {
  margin: 6px 0 0;
  color: var(--ink-2);
  font-size: 13px;
}

.seckill-feedback {
  margin-top: 12px;
  font-size: 13px;
  color: var(--ink-2);
}

.detail-tabs {
  grid-column: 1 / 2;
}

.detail-tabs h3,
.recommend h3 {
  margin: 0;
  color: var(--ink-1);
}

.detail-tabs p {
  margin: 10px 0 0;
  color: var(--ink-2);
  line-height: 1.8;
}

.detail-tabs ul {
  margin: 12px 0 0;
  color: var(--ink-2);
  padding-left: 18px;
  line-height: 1.8;
}

.recommend {
  grid-column: 2 / 3;
}

.recommend-list {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.recommend-item {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 8px;
  background: #ffffff;
}

.recommend-item img {
  width: 100%;
  aspect-ratio: 16 / 10;
  object-fit: cover;
  border-radius: 8px;
}

.recommend-item p {
  margin: 8px 0 0;
  color: var(--ink-1);
  font-size: 13px;
}

.pay-modal-content {
  display: grid;
  gap: 8px;
  color: var(--ink-2);
}

.pay-modal-content p {
  margin: 0;
}

.pay-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 960px) {
  .detail-page {
    padding: 16px;
  }

  .layout-grid {
    grid-template-columns: 1fr;
  }

  .detail-tabs,
  .recommend {
    grid-column: auto;
  }
}
</style>