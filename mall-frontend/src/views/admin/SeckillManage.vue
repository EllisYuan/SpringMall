<template>
  <div class="seckill-manage-page">
    <!-- 页头 -->
    <div class="page-header">
      <h2 class="page-title">秒杀管理</h2>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        新建活动
      </el-button>
    </div>

    <!-- 活动列表 -->
    <el-card class="table-card">
      <el-table v-loading="loading" :data="activities" stripe>
        <el-table-column prop="id" label="ID" width="180" show-overflow-tooltip />
        <el-table-column prop="activityName" label="活动名称" min-width="180" />
        <el-table-column label="商品" min-width="160">
          <template #default="{ row }">
            <div class="cell-product">
              <span class="cell-product__name">{{ row.productName || `#${row.productId}` }}</span>
              <span v-if="row.skuSpecDesc" class="cell-product__sku">{{ row.skuSpecDesc }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="秒杀价" width="100">
          <template #default="{ row }">
            <span class="price-seckill">¥{{ formatPrice(row.seckillPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="原价" width="100">
          <template #default="{ row }">¥{{ formatPrice(row.originalPrice) }}</template>
        </el-table-column>
        <el-table-column label="库存" width="120">
          <template #default="{ row }">
            {{ row.availableStock }} / {{ row.seckillStock }}
          </template>
        </el-table-column>
        <el-table-column label="限购" width="80">
          <template #default="{ row }">{{ row.limitPerUser }} 件</template>
        </el-table-column>
        <el-table-column label="开始时间" width="160">
          <template #default="{ row }">{{ formatDate(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="160">
          <template #default="{ row }">{{ formatDate(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="activityTagType(row.activityStatus)" size="small">
              {{ activityStatusText(row.activityStatus) }}
            </el-tag>
            <el-tag
              class="status-tag"
              :type="row.status === 1 ? 'success' : 'info'"
              size="small"
            >
              {{ row.status === 1 ? '已上线' : '已下线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="二次流出" width="90">
          <template #default="{ row }">
            <el-tag :type="row.allowRestock === 0 ? 'info' : 'success'" size="small">
              {{ row.allowRestock === 0 ? '不允许' : '允许' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <!-- 已上线禁编辑：需先下线 -->
            <el-tooltip v-if="row.status === 1" content="请先下线后再编辑" placement="top">
              <span class="btn-disabled-wrap">
                <el-button type="primary" link size="small" disabled>编辑</el-button>
              </span>
            </el-tooltip>
            <el-button v-else type="primary" link size="small" @click="openEdit(row)">编辑</el-button>

            <!-- 已上线：重新预热 + 下线；草稿/下线：上线 + 删除 -->
            <template v-if="row.status === 1">
              <el-button type="success" link size="small" @click="handlePreheat(row)">重新预热</el-button>
              <el-button type="warning" link size="small" @click="handleOffline(row)">下线</el-button>
            </template>
            <template v-else>
              <el-button type="primary" link size="small" @click="handleActivate(row)">上线</el-button>
              <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchActivities"
          @current-change="fetchActivities"
        />
      </div>
    </el-card>

    <!-- 新建/编辑 Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑秒杀活动' : '新建秒杀活动'"
      width="600px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-alert
        v-if="!isEdit"
        title="创建后活动为草稿态，需在列表点击「上线」后才生效（上线将预留商品库存并自动预热）"
        type="info"
        :closable="false"
        show-icon
        class="dialog-alert"
      />

      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="100px"
        label-position="right"
      >
        <el-form-item label="活动名称" prop="activityName">
          <el-input v-model="form.activityName" placeholder="请输入活动名称" maxlength="100" />
        </el-form-item>

        <el-form-item label="商品" prop="productId">
          <EntityPickerField
            v-model="form.productId"
            :config="onSaleProductPicker"
            :initial-row="currentProduct"
            placeholder="选择商品"
            @change="onProductChange"
          />
        </el-form-item>

        <el-form-item v-if="currentProduct && currentProduct.hasSku === 1" label="SKU" prop="skuId">
          <el-select v-model="form.skuId" placeholder="选择规格" style="width: 100%" @change="onSkuChange">
            <el-option
              v-for="s in skuOptions"
              :key="s.id"
              :label="`${s.specDesc}（¥${s.price} · 库存${s.stock}）`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="原价" prop="originalPrice">
          <el-input v-model.number="form.originalPrice" placeholder="0.00" type="number" :min="0">
            <template #prepend>¥</template>
          </el-input>
        </el-form-item>

        <el-form-item label="秒杀价" prop="seckillPrice">
          <el-input v-model.number="form.seckillPrice" placeholder="0.00" type="number" :min="0">
            <template #prepend>¥</template>
          </el-input>
        </el-form-item>

        <el-form-item label="秒杀库存" prop="seckillStock">
          <el-input v-model.number="form.seckillStock" placeholder="库存数量" type="number" :min="1" />
        </el-form-item>

        <el-form-item label="每人限购" prop="limitPerUser">
          <el-input v-model.number="form.limitPerUser" placeholder="每人最多购买件数" type="number" :min="1" />
        </el-form-item>

        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            placeholder="选择开始时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            placeholder="选择结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="二次流出">
          <div class="restock-field">
            <el-switch
              v-model="form.allowRestock"
              :active-value="1"
              :inactive-value="0"
              active-text="允许"
              inactive-text="不允许"
            />
            <p class="restock-hint">
              超时未付释放的库存是否重新放出：<strong>允许</strong>（默认）则回补库存并清除售罄标记，重新公开先到先得；
              <strong>不允许</strong>则未付库存活动期内不再放出，杜绝“晚到者捡漏”，代价是成交量可能低于秒杀库存。
            </p>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEdit ? '保存修改' : '创建活动' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  adminGetSeckillActivities,
  adminGetSeckillActivity,
  adminCreateSeckillActivity,
  adminUpdateSeckillActivity,
  adminActivateSeckillActivity,
  adminOfflineSeckillActivity,
  adminDeleteSeckillActivity,
  adminPreheatSeckillActivity
} from '@/api/seckill'
import { getProductById } from '@/api/admin/product'
import { formatPrice, formatDate } from '@/utils/format'
import EntityPickerField from '@/components/common/EntityPickerField.vue'
import { onSaleProductPicker } from '@/components/common/pickerPresets'

// ── 商品/SKU 选择联动 ────────────────────────────────────
const currentProduct = ref(null)   // 当前所选商品详情（含 hasSku/skuList），同时作为选择器回显的 initialRow
const skuOptions = ref([])          // 当前商品的 SKU 列表

/**
 * 拉取商品详情（管理端接口，可取下架商品），填充 SKU 级联选项
 * @param productId       商品ID
 * @param resetSku        是否重置已选 SKU（切换商品时 true，编辑回显时 false）
 * @param prefillPrice    是否用商品价自动回填原价（用户主动选商品时 true，编辑回显时 false）
 */
const loadProductContext = async (productId, { resetSku = false, prefillPrice = false } = {}) => {
  if (resetSku) form.skuId = null
  if (!productId) {
    currentProduct.value = null
    skuOptions.value = []
    return
  }
  try {
    const detail = await getProductById(productId)
    currentProduct.value = detail
    skuOptions.value = detail.hasSku === 1 ? (detail.skuList || []) : []
    // 自动填入原价（无 SKU 商品直接用商品价；有 SKU 的待选 SKU 后由 onSkuChange 精确覆盖）
    if (prefillPrice && detail.hasSku !== 1 && detail.price != null) {
      form.originalPrice = detail.price
    }
  } catch (e) {
    console.error('获取商品详情失败:', e)
    currentProduct.value = null
    skuOptions.value = []
  }
}

// 选择器回调：换商品时重置 SKU 并自动填原价
const onProductChange = (row) => {
  if (row) {
    loadProductContext(row.id, { resetSku: true, prefillPrice: true })
  } else {
    currentProduct.value = null
    skuOptions.value = []
    form.skuId = null
  }
}

// 选中 SKU 后用该规格价格精确回填原价
const onSkuChange = (skuId) => {
  const sku = skuOptions.value.find(s => s.id === skuId)
  if (sku && sku.price != null) {
    form.originalPrice = sku.price
  }
}

// ── 列表 ─────────────────────────────────────────────────
const loading = ref(false)
const activities = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const fetchActivities = async () => {
  loading.value = true
  try {
    const data = await adminGetSeckillActivities({ page: page.value, size: pageSize.value })
    // 后端可能返回 PageResult 或 List
    if (data && typeof data === 'object' && Array.isArray(data.list)) {
      activities.value = data.list
      total.value = data.total || data.list.length
    } else if (Array.isArray(data)) {
      activities.value = data
      total.value = data.length
    } else {
      activities.value = []
      total.value = 0
    }
  } catch (error) {
    console.error('获取秒杀活动列表失败:', error)
  } finally {
    loading.value = false
  }
}

// ── 状态辅助 ─────────────────────────────────────────────
const activityStatusText = (s) => {
  const map = { NOT_STARTED: '未开始', IN_PROGRESS: '进行中', SOLD_OUT: '已售罄', ENDED: '已结束' }
  return map[s] || s || '-'
}

const activityTagType = (s) => {
  if (s === 'IN_PROGRESS') return 'danger'
  if (s === 'NOT_STARTED') return 'warning'
  if (s === 'SOLD_OUT' || s === 'ENDED') return 'info'
  return ''
}

// ── 重新预热（仅已上线，覆盖写 Redis 分桶 + 重置闸门） ────────
const handlePreheat = async (row) => {
  try {
    await ElMessageBox.confirm(
      '重新预热将以当前剩余库存覆盖 Redis 快照；若本活动关闭了二次流出，已回收但未放出的库存也会被重新放出。确定要重新预热吗？',
      '重新预热',
      { confirmButtonText: '确定预热', cancelButtonText: '取消', type: 'warning' }
    )
    await adminPreheatSeckillActivity(row.id)
    ElMessage.success('重新预热成功，库存快照已刷新到 Redis')
  } catch (error) {
    if (error !== 'cancel') console.error('预热失败:', error)
  }
}

// ── 上线（预留商品库存 + 自动预热） ──────────────────────────
const handleActivate = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要上线活动「${row.activityName}」吗？上线将从商品库存预留 ${row.seckillStock} 件并自动预热。`,
      '上线活动',
      { confirmButtonText: '确定上线', cancelButtonText: '取消', type: 'warning' }
    )
    await adminActivateSeckillActivity(row.id)
    ElMessage.success('上线成功')
    fetchActivities()
  } catch (e) {
    if (e !== 'cancel') console.error('上线失败:', e)
  }
}

// ── 下线（归还未售库存回商品库存） ──────────────────────────
const handleOffline = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要下线活动「${row.activityName}」吗？未售库存将归还商品库存。`,
      '下线活动',
      { confirmButtonText: '确定下线', cancelButtonText: '取消', type: 'warning' }
    )
    await adminOfflineSeckillActivity(row.id)
    ElMessage.success('下线成功')
    fetchActivities()
  } catch (e) {
    if (e !== 'cancel') console.error('下线失败:', e)
  }
}

// ── 删除 ─────────────────────────────────────────────────
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除活动「${row.activityName}」吗？此操作不可恢复。`,
      '删除活动',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'error' }
    )
    await adminDeleteSeckillActivity(row.id)
    ElMessage.success('删除成功')
    fetchActivities()
  } catch (e) {
    if (e !== 'cancel') console.error('删除失败:', e)
  }
}

// ── Dialog 表单 ───────────────────────────────────────────
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const editingId = ref(null)

const emptyForm = () => ({
  activityName: '',
  productId: null,
  skuId: null,
  originalPrice: null,
  seckillPrice: null,
  seckillStock: null,
  limitPerUser: 1,
  startTime: '',
  endTime: '',
  allowRestock: 1
})

const form = reactive(emptyForm())

const formRules = {
  activityName: [{ required: true, message: '请输入活动名称', trigger: 'blur' }],
  productId: [{ required: true, message: '请选择商品', trigger: 'change' }],
  originalPrice: [{ required: true, message: '请输入原价', trigger: 'blur' }],
  seckillPrice: [{ required: true, message: '请输入秒杀价', trigger: 'blur' }],
  seckillStock: [{ required: true, message: '请输入库存数量', trigger: 'blur' }],
  limitPerUser: [{ required: true, message: '请输入每人限购数量', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}

const buildFormData = (source) => ({
  activityName: source.activityName,
  productId: source.productId,
  skuId: source.skuId || 0,
  originalPrice: source.originalPrice,
  seckillPrice: source.seckillPrice,
  seckillStock: source.seckillStock,
  limitPerUser: source.limitPerUser,
  startTime: source.startTime,
  endTime: source.endTime,
  allowRestock: source.allowRestock ?? 1
})

const openCreate = () => {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, emptyForm())
  currentProduct.value = null
  skuOptions.value = []
  dialogVisible.value = true
}

const openEdit = async (row) => {
  isEdit.value = true
  editingId.value = row.id
  try {
    // 获取最新详情，确保数据完整
    const detail = await adminGetSeckillActivity(row.id)
    // 先加载商品上下文（设好 currentProduct=initialRow + skuOptions），
    // 再赋值表单 —— 这样选择器字段的 modelValue 监听触发时已能复用 initialRow，避免重复请求商品详情（W1）。
    // 编辑态不重置已选 skuId、不覆盖已存原价快照。
    await loadProductContext(detail.productId, { resetSku: false, prefillPrice: false })
    Object.assign(form, {
      activityName: detail.activityName,
      productId: detail.productId,
      skuId: detail.skuId || 0,
      originalPrice: detail.originalPrice,
      seckillPrice: detail.seckillPrice,
      seckillStock: detail.seckillStock,
      limitPerUser: detail.limitPerUser,
      startTime: detail.startTime,
      endTime: detail.endTime,
      allowRestock: detail.allowRestock ?? 1
    })
    dialogVisible.value = true
  } catch (error) {
    console.error('获取活动详情失败:', error)
  }
}

const resetForm = () => {
  formRef.value?.resetFields()
  Object.assign(form, emptyForm())
  currentProduct.value = null
  skuOptions.value = []
}

const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  // 有 SKU 的商品必须选定规格，避免创建 sku_id=0 的错误活动
  if (currentProduct.value?.hasSku === 1 && !form.skuId) {
    ElMessage.warning('请选择商品规格(SKU)')
    return
  }

  if (form.seckillPrice >= form.originalPrice) {
    ElMessage.warning('秒杀价应低于原价')
    return
  }

  if (form.startTime >= form.endTime) {
    ElMessage.warning('结束时间应晚于开始时间')
    return
  }

  submitting.value = true
  try {
    const payload = buildFormData(form)
    if (isEdit.value) {
      await adminUpdateSeckillActivity(editingId.value, payload)
      ElMessage.success('活动更新成功')
    } else {
      await adminCreateSeckillActivity(payload)
      ElMessage.success('活动创建成功')
    }
    dialogVisible.value = false
    fetchActivities()
  } catch (error) {
    console.error('保存活动失败:', error)
  } finally {
    submitting.value = false
  }
}

onMounted(fetchActivities)
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.seckill-manage-page {
  padding: $spacing-md;
}

.page-header {
  @include flex-between;
  margin-bottom: $spacing-lg;

  .page-title {
    font-size: $font-size-lg;
    font-weight: $font-weight-bold;
    color: $text-primary;
  }
}

.table-card {
  margin-bottom: $spacing-lg;
}

.price-seckill {
  color: $danger-color;
  font-weight: $font-weight-medium;
}

.cell-product {
  display: flex;
  flex-direction: column;
  line-height: 1.3;

  &__name {
    color: $text-primary;
  }

  &__sku {
    font-size: $font-size-sm;
    color: $text-secondary;
  }
}

.status-tag {
  margin-left: 4px;
}

.btn-disabled-wrap {
  // 包裹禁用按钮，使 el-tooltip 在按钮 disabled 时仍能触发悬浮提示
  display: inline-block;
}

.dialog-alert {
  margin-bottom: $spacing-md;
}

.restock-field {
  width: 100%;
}

.restock-hint {
  margin-top: $spacing-xs;
  font-size: $font-size-sm;
  color: $text-secondary;
  line-height: $line-height-base;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: $spacing-lg;
}
</style>
