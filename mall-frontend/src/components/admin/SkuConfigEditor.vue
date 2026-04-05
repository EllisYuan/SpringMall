<template>
  <div class="sku-config-editor">
    <Loading v-if="loadingConfig" />

    <template v-else>
      <!-- 规格维度配置 -->
      <div class="section-title">规格维度</div>

      <div v-if="specs.length === 0" class="empty-hint">
        暂未配置规格，点击下方按钮添加
      </div>

      <div
        v-for="(spec, sIdx) in specs"
        :key="sIdx"
        class="spec-block"
        draggable="true"
        @dragstart="onSpecDragStart(sIdx)"
        @dragover.prevent
        @drop="onSpecDrop(sIdx)"
      >
        <div class="spec-header">
          <span class="drag-handle" title="拖动排序">☰</span>
          <el-input
            v-model="spec.name"
            placeholder="规格名称（如：颜色）"
            style="width: 200px"
            size="small"
          />
          <el-button
            type="danger"
            text
            size="small"
            @click="removeSpec(sIdx)"
          >删除规格</el-button>
        </div>

        <div class="spec-values">
          <el-tag
            v-for="(val, vIdx) in spec.values"
            :key="vIdx"
            class="spec-value-tag"
            closable
            size="small"
            draggable="true"
            @dragstart="onValueDragStart(sIdx, vIdx)"
            @dragover.prevent
            @drop="onValueDrop(sIdx, vIdx)"
            @close="removeSpecValue(sIdx, vIdx)"
          >
            {{ val.value }}
          </el-tag>
          <el-input
            v-model="newValueInputs[sIdx]"
            placeholder="添加选项值"
            size="small"
            style="width: 120px"
            @keyup.enter="addSpecValue(sIdx)"
          />
          <el-button size="small" @click="addSpecValue(sIdx)">添加</el-button>
        </div>
      </div>

      <el-button size="small" @click="addSpec">+ 添加规格维度</el-button>

      <!-- SKU 列表表格（笛卡尔积） -->
      <template v-if="skuTableRows.length > 0">
        <div class="section-title" style="margin-top: 24px">SKU 列表（共 {{ skuTableRows.length }} 个）</div>

        <el-table :data="skuTableRows" border size="small" class="sku-table">
          <el-table-column label="规格组合" min-width="180">
            <template #default="{ row }">
              {{ row.specDesc }}
            </template>
          </el-table-column>
          <el-table-column label="SKU 编码" min-width="160">
            <template #default="{ row }">
              <el-input
                v-model="row.skuCode"
                placeholder="（可选）SKU 编码"
                size="small"
              />
            </template>
          </el-table-column>
          <el-table-column label="价格" width="140">
            <template #default="{ row }">
              <el-input-number
                v-model="row.price"
                :min="0.01"
                :step="0.01"
                :precision="2"
                size="small"
                style="width: 110px"
              />
            </template>
          </el-table-column>
          <el-table-column label="库存" width="120">
            <template #default="{ row }">
              <el-input-number
                v-model="row.stock"
                :min="0"
                size="small"
                style="width: 90px"
              />
            </template>
          </el-table-column>
          <el-table-column label="图片URL" min-width="200">
            <template #default="{ row }">
              <el-input
                v-model="row.image"
                placeholder="（可选）图片URL"
                size="small"
              />
            </template>
          </el-table-column>
          <el-table-column label="默认" width="80" align="center">
            <template #default="{ row, $index }">
              <el-radio
                :model-value="defaultSkuIndex"
                :value="$index"
                @change="setDefaultSku($index)"
              >&nbsp;</el-radio>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- 操作按钮 -->
      <div class="action-bar">
        <el-button
          type="warning"
          plain
          :loading="applying"
          :disabled="specs.length === 0"
          @click="handleApply"
        >
          应用规格配置
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="specs.length === 0"
          @click="handleSave"
        >
          保存规格配置
        </el-button>
        <el-button
          type="danger"
          plain
          :loading="deleting"
          @click="handleDelete"
        >
          清空规格配置
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getSkuConfig, saveSkuConfig, deleteSkuConfig } from '@/api/admin/sku'
import { ElMessage, ElMessageBox } from 'element-plus'
import Loading from '@/components/common/Loading.vue'

const props = defineProps({
  productId: {
    type: [Number, String],
    required: true
  }
})

const emit = defineEmits(['saved'])

const loadingConfig = ref(false)
const applying = ref(false)
const saving = ref(false)
const deleting = ref(false)

const draggingSpecIndex = ref(-1)
const draggingValueSpecIndex = ref(-1)
const draggingValueIndex = ref(-1)

// 当前默认 SKU 的行索引，-1 表示无默认
const defaultSkuIndex = ref(-1)

// 规格维度列表，格式：[{ name, sortOrder, values: [{ value, sortOrder }] }]
const specs = ref([])

// 每个规格维度的新值输入框绑定
const newValueInputs = ref([])

// 生成笛卡尔积 SKU 行列表
// 每行对应一个 SKU，包含：specDesc、specFlatIndices（扁平序号数组）、price、stock、image
const skuTableRows = ref([])

const rebuildSkuTable = () => {
  const specValueGroups = specs.value.map((spec, sIdx) =>
    spec.values.map((val, vIdx) => {
      // 计算该 value 在扁平序号中的位置
      let flatIdx = 0
      for (let i = 0; i < sIdx; i++) flatIdx += specs.value[i].values.length
      flatIdx += vIdx
      return { label: val.value, flatIdx }
    })
  ).filter(g => g.length > 0)

  if (specValueGroups.length === 0) {
    skuTableRows.value = []
    return
  }

  // 笛卡尔积
  const cartesian = (arrays) => {
    return arrays.reduce(
      (acc, curr) => acc.flatMap(a => curr.map(c => [...a, c])),
      [[]]
    )
  }

  const combinations = cartesian(specValueGroups)

  // 保留已有行的 price/stock/image/skuCode/isDefault（按 specDesc 匹配）
  const oldRowMap = {}
  skuTableRows.value.forEach(row => {
    oldRowMap[row.specDesc] = { price: row.price, stock: row.stock, image: row.image, skuCode: row.skuCode, isDefault: row.isDefault }
  })

  skuTableRows.value = combinations.map(combo => {
    const specDesc = combo.map(c => c.label).join(',')
    const specFlatIndices = combo.map(c => c.flatIdx)
    const old = oldRowMap[specDesc] || {}
    return {
      specDesc,
      specFlatIndices,
      skuCode: old.skuCode || '',
      price: old.price !== undefined ? old.price : 0.01,
      stock: old.stock !== undefined ? old.stock : 0,
      image: old.image || '',
      isDefault: old.isDefault || false
    }
  })

  // 同步 defaultSkuIndex
  const newDefaultIdx = skuTableRows.value.findIndex(r => r.isDefault)
  defaultSkuIndex.value = newDefaultIdx >= 0 ? newDefaultIdx : -1
}

const validateSpecs = () => {
  for (const spec of specs.value) {
    if (!spec.name.trim()) {
      ElMessage.warning('规格名称不能为空')
      return false
    }
    if (spec.values.length === 0) {
      ElMessage.warning(`规格 "${spec.name}" 下至少需要一个选项值`)
      return false
    }
  }

  if (skuTableRows.value.length === 0) {
    ElMessage.warning('请至少配置一个规格维度并添加选项值')
    return false
  }

  return true
}

const buildPayload = () => ({
  specs: specs.value.map((spec, sIdx) => ({
    name: spec.name,
    sortOrder: sIdx,
    values: spec.values.map((val, vIdx) => ({
      value: val.value,
      sortOrder: vIdx
    }))
  })),
  skuList: skuTableRows.value.map((row, idx) => ({
    specValueIds: row.specFlatIndices,
    skuCode: row.skuCode || null,
    price: row.price,
    stock: row.stock,
    image: row.image || null,
    isDefault: idx === defaultSkuIndex.value
  }))
})

// 加载已有 SKU 配置
const loadConfig = async () => {
  loadingConfig.value = true
  try {
    const data = await getSkuConfig(props.productId)
    if (data && data.specs && data.specs.length > 0) {
      specs.value = data.specs.map((s, sIdx) => ({
        name: s.name,
        sortOrder: s.sortOrder || sIdx,
        values: (s.values || []).map((v, vIdx) => ({
          value: v.value,
          sortOrder: v.sortOrder || vIdx
        }))
      }))
      newValueInputs.value = specs.value.map(() => '')

      // 填充后端已有的 SKU 数据
      if (data.skuList && data.skuList.length > 0) {
        // 先 rebuild 生成空行，再按 specFlatIndices 填充价格库存
        rebuildSkuTable()
        data.skuList.forEach(backendSku => {
          const sortedIds = [...(backendSku.specValueIds || [])].sort((a, b) => a - b)
          const row = skuTableRows.value.find(r => {
            const rowSorted = [...r.specFlatIndices].sort((a, b) => a - b)
            return rowSorted.length === sortedIds.length && rowSorted.every((v, i) => v === sortedIds[i])
          })
          if (row) {
            row.skuCode = backendSku.skuCode || ''
            row.price = backendSku.price || 0.01
            row.stock = backendSku.stock || 0
            row.image = backendSku.image || ''
            row.isDefault = backendSku.isDefault || false
          }
        })

        // 同步 defaultSkuIndex
        const loadedDefaultIdx = skuTableRows.value.findIndex(r => r.isDefault)
        defaultSkuIndex.value = loadedDefaultIdx >= 0 ? loadedDefaultIdx : -1
      }
    }
  } catch (error) {
    // 无配置时 404，忽略
    console.log('暂无 SKU 配置:', error)
  } finally {
    loadingConfig.value = false
  }
}

const addSpec = () => {
  specs.value.push({ name: '', sortOrder: specs.value.length, values: [] })
  newValueInputs.value.push('')
  rebuildSkuTable()
}

const removeSpec = (sIdx) => {
  specs.value.splice(sIdx, 1)
  newValueInputs.value.splice(sIdx, 1)
  rebuildSkuTable()
}

const addSpecValue = (sIdx) => {
  const val = (newValueInputs.value[sIdx] || '').trim()
  if (!val) return
  const spec = specs.value[sIdx]
  if (spec.values.some(v => v.value === val)) {
    ElMessage.warning('该选项值已存在')
    return
  }
  spec.values.push({ value: val, sortOrder: spec.values.length })
  newValueInputs.value[sIdx] = ''
  rebuildSkuTable()
}

const removeSpecValue = (sIdx, vIdx) => {
  specs.value[sIdx].values.splice(vIdx, 1)
  rebuildSkuTable()
}

const moveArrayItem = (arr, fromIndex, toIndex) => {
  if (fromIndex === toIndex || fromIndex < 0 || toIndex < 0 || fromIndex >= arr.length || toIndex >= arr.length) {
    return
  }
  const [movedItem] = arr.splice(fromIndex, 1)
  arr.splice(toIndex, 0, movedItem)
}

const onSpecDragStart = (index) => {
  draggingSpecIndex.value = index
}

const onSpecDrop = (targetIndex) => {
  const sourceIndex = draggingSpecIndex.value
  draggingSpecIndex.value = -1
  if (sourceIndex < 0 || sourceIndex === targetIndex) return

  moveArrayItem(specs.value, sourceIndex, targetIndex)
  moveArrayItem(newValueInputs.value, sourceIndex, targetIndex)

  specs.value.forEach((spec, sIdx) => {
    spec.sortOrder = sIdx
  })

  rebuildSkuTable()
}

const onValueDragStart = (specIndex, valueIndex) => {
  draggingValueSpecIndex.value = specIndex
  draggingValueIndex.value = valueIndex
}

const onValueDrop = (targetSpecIndex, targetValueIndex) => {
  const sourceSpecIndex = draggingValueSpecIndex.value
  const sourceValueIndex = draggingValueIndex.value
  draggingValueSpecIndex.value = -1
  draggingValueIndex.value = -1

  if (sourceSpecIndex < 0 || sourceValueIndex < 0) return
  if (sourceSpecIndex !== targetSpecIndex) return
  if (sourceValueIndex === targetValueIndex) return

  const values = specs.value[targetSpecIndex].values
  moveArrayItem(values, sourceValueIndex, targetValueIndex)
  values.forEach((val, idx) => {
    val.sortOrder = idx
  })

  rebuildSkuTable()
}

const handleApply = async () => {
  if (!validateSpecs()) return

  applying.value = true
  try {
    rebuildSkuTable()
    await saveSkuConfig(props.productId, buildPayload())
    ElMessage.success('规格配置已应用并保存')
    emit('saved', { action: 'apply' })
  } finally {
    applying.value = false
  }
}

const handleSave = async () => {
  if (!validateSpecs()) return

  saving.value = true
  try {
    await saveSkuConfig(props.productId, buildPayload())
    ElMessage.success('规格配置保存成功')
    emit('saved', { action: 'save' })
  } catch (error) {
    console.error('保存规格配置失败:', error)
  } finally {
    saving.value = false
  }
}

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm('确定要清空该商品的规格配置吗？此操作不可恢复。', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    deleting.value = true
    await deleteSkuConfig(props.productId)
    ElMessage.success('规格配置已清空')
    specs.value = []
    newValueInputs.value = []
    skuTableRows.value = []
    defaultSkuIndex.value = -1
    emit('saved', { action: 'delete' })
  } catch (error) {
    // 用户取消或接口报错，拦截器已处理
  } finally {
    deleting.value = false
  }
}

const setDefaultSku = (index) => {
  defaultSkuIndex.value = index
}

onMounted(loadConfig)
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.sku-config-editor {
  padding: $spacing-md 0;
}

.section-title {
  font-size: $font-size-sm;
  font-weight: $font-weight-bold;
  letter-spacing: 0.04em;
  color: $text-secondary;
  text-transform: uppercase;
  margin-bottom: $spacing-md;
}

.empty-hint {
  font-size: $font-size-sm;
  color: $text-placeholder;
  margin-bottom: $spacing-md;
}

.spec-block {
  border: 1px solid $border-light;
  padding: $spacing-md;
  margin-bottom: $spacing-md;
  background: $bg-page;
}

.spec-header {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-sm;
}

.drag-handle {
  color: $text-secondary;
  cursor: grab;
  user-select: none;
  font-size: 16px;
  line-height: 1;
}

.spec-values {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: $spacing-sm;
}

.spec-value-tag {
  cursor: grab;
  user-select: none;
}

.sku-table {
  margin-bottom: $spacing-md;
}

.action-bar {
  display: flex;
  gap: $spacing-md;
  margin-top: $spacing-lg;
}
</style>
