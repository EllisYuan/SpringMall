<template>
  <div class="entity-picker-field">
    <!-- 已选：展示名称（非 ID），带更换/清除 -->
    <div v-if="modelValue" class="picked">
      <span class="picked-label">
        {{ displayLabel }}
        <span class="picked-id">#{{ modelValue }}</span>
      </span>
      <div class="picked-actions">
        <el-button v-if="!disabled" type="primary" link size="small" @click="open">更换</el-button>
        <el-button v-if="!disabled" type="danger" link size="small" @click="clear">清除</el-button>
      </div>
    </div>

    <!-- 未选：选择按钮 -->
    <el-button v-else :disabled="disabled" @click="open">
      <el-icon><Plus /></el-icon>
      {{ placeholder || '选择' }}
    </el-button>

    <EntityPickerDialog v-model:visible="dialogVisible" :config="config" @select="onSelect" />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import EntityPickerDialog from './EntityPickerDialog.vue'

const props = defineProps({
  // 绑定值 = 实体 id（提交给后端的是它，不是名称）
  modelValue: { type: [Number, String], default: null },
  config: { type: Object, required: true },
  placeholder: { type: String, default: '选择' },
  disabled: { type: Boolean, default: false },
  // 父组件已持有的实体对象（编辑态回显复用，避免字段再次 fetchById 造成重复请求）
  initialRow: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'change'])

const dialogVisible = ref(false)
// 已选实体对象，用于展示名称；编辑态可能先有 id 后补对象
const selectedRow = ref(null)

const labelField = computed(() => props.config.labelField || 'name')

const displayLabel = computed(() => {
  if (selectedRow.value) return selectedRow.value[labelField.value]
  return '加载中…'
})

const open = () => { dialogVisible.value = true }

const onSelect = (row) => {
  selectedRow.value = row
  emit('update:modelValue', row[props.config.valueField || 'id'])
  emit('change', row)
}

const clear = () => {
  selectedRow.value = null
  emit('update:modelValue', null)
  emit('change', null)
}

// 请求序号：丢弃过期的 fetchById 响应，防止快速更换时旧响应覆盖新选（W3）
let reqToken = 0

// 编辑态回显：modelValue 有值但还没有对应展示对象时，按 id 拉取详情
watch(
  () => props.modelValue,
  async (val) => {
    if (!val) {
      selectedRow.value = null
      return
    }
    const idField = props.config.valueField || 'id'
    if (selectedRow.value && selectedRow.value[idField] === val) return
    // 父组件已提供匹配的实体对象 → 直接复用，跳过自身请求（W1 去重）
    if (props.initialRow && props.initialRow[idField] === val) {
      selectedRow.value = props.initialRow
      return
    }
    if (typeof props.config.fetchById === 'function') {
      const token = ++reqToken
      try {
        const row = await props.config.fetchById(val)
        if (token === reqToken) selectedRow.value = row
      } catch (e) {
        console.error('回显实体失败:', e)
        // 失败降级：展示 #id，避免永久停在「加载中…」（W2）
        if (token === reqToken) {
          selectedRow.value = { [idField]: val, [labelField.value]: `#${val}` }
        }
      }
    } else {
      // 无 fetchById（如 userPicker）：降级展示 #id
      selectedRow.value = { [idField]: val, [labelField.value]: `#${val}` }
    }
  },
  { immediate: true }
)
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.entity-picker-field {
  width: 100%;
}

.picked {
  @include flex-between;
  gap: $spacing-md;
  padding: 6px 12px;
  border: 1px solid $border-base;
  border-radius: 4px;
  background: $bg-gray;
}

.picked-label {
  @include text-ellipsis;
  font-size: $font-size-base;
  color: $text-primary;
}

.picked-id {
  margin-left: $spacing-sm;
  font-size: $font-size-sm;
  color: $text-placeholder;
}

.picked-actions {
  flex-shrink: 0;
  white-space: nowrap;
}
</style>
