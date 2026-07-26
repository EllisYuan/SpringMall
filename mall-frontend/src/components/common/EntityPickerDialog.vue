<template>
  <el-dialog
    :model-value="visible"
    :title="config.title || '选择'"
    width="720px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:visible', $event)"
    @open="onOpen"
  >
    <!-- 搜索 -->
    <div class="picker-search">
      <el-input
        v-model="keyword"
        :placeholder="config.searchPlaceholder || '输入关键词搜索'"
        clearable
        @input="onKeywordInput"
        @clear="reload"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>

    <!-- 结果表格 -->
    <el-table
      v-loading="loading"
      :data="list"
      height="380"
      stripe
      :row-class-name="rowClassName"
      @row-dblclick="onPick"
    >
      <el-table-column
        v-for="col in config.columns"
        :key="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
      >
        <template #default="{ row }">
          <!-- 图片 -->
          <el-image
            v-if="col.type === 'image'"
            :src="row[col.prop]"
            fit="cover"
            class="picker-thumb"
          >
            <template #error><div class="picker-thumb picker-thumb--ph" /></template>
          </el-image>
          <!-- 价格 -->
          <span v-else-if="col.type === 'price'" class="picker-price">
            ¥{{ formatPrice(row[col.prop]) }}
          </span>
          <!-- 状态标签 -->
          <el-tag
            v-else-if="col.type === 'status'"
            :type="(col.statusMap?.[row[col.prop]]?.type) || 'info'"
            size="small"
          >
            {{ col.statusMap?.[row[col.prop]]?.text ?? row[col.prop] }}
          </el-tag>
          <!-- 普通文本 -->
          <span v-else>{{ row[col.prop] }}</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <span v-if="isDisabled(row)" class="picker-disabled-tip">
            {{ config.disabledTip || '不可选' }}
          </span>
          <el-button v-else type="primary" link size="small" @click="onPick(row)">选择</el-button>
        </template>
      </el-table-column>

      <template #empty>
        <Empty type="search" text="未找到匹配结果" />
      </template>
    </el-table>

    <!-- 分页 -->
    <div class="picker-pagination">
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        small
        @current-change="fetchData"
      />
    </div>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { debounce } from '@/utils/helpers'
import { formatPrice } from '@/utils/format'
import Empty from '@/components/common/Empty.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  // 实体预设配置：{ title, searchPlaceholder, fetchList(params)->{list,total}, columns,
  //               rowDisabled?(row)->boolean, disabledTip? }
  config: { type: Object, required: true }
})

const emit = defineEmits(['update:visible', 'select'])

const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const list = ref([])
const loading = ref(false)

const fetchData = async () => {
  loading.value = true
  try {
    const data = await props.config.fetchList({
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value
    })
    // 兼容 PageResult({list,total}) 与纯数组两种返回
    if (Array.isArray(data)) {
      list.value = data
      total.value = data.length
    } else {
      list.value = data?.list || []
      total.value = data?.total || 0
    }
  } catch (e) {
    console.error('查询失败:', e)
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 关键词变化重置到第一页再查
const reload = () => {
  page.value = 1
  fetchData()
}
const onKeywordInput = debounce(reload, 400)

// 弹窗打开时重置并加载
const onOpen = () => {
  keyword.value = ''
  page.value = 1
  fetchData()
}

// 行禁用：预设未提供 rowDisabled 时全部可选（保持既有行为）
const isDisabled = (row) =>
  typeof props.config.rowDisabled === 'function' && props.config.rowDisabled(row)

const rowClassName = ({ row }) => (isDisabled(row) ? 'picker-row--disabled' : '')

const onPick = (row) => {
  if (isDisabled(row)) return
  emit('select', row)
  emit('update:visible', false)
}
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.picker-search {
  margin-bottom: $spacing-md;
}

.picker-thumb {
  width: 44px;
  height: 44px;
  display: block;
  border-radius: 2px;
  background: $bg-gray;

  &--ph {
    background: $border-light;
  }
}

.picker-price {
  color: $danger-color;
  font-weight: $font-weight-medium;
}

.picker-disabled-tip {
  font-size: $font-size-sm;
  color: $text-placeholder;
}

// 禁用行整体置灰（el-table 的行 class 落在 tr 上，需穿透 scoped）
:deep(.picker-row--disabled) {
  color: $text-placeholder;
  cursor: not-allowed;

  .picker-price {
    color: $text-placeholder;
  }

  .el-image,
  .el-tag {
    opacity: 0.5;
  }
}

.picker-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: $spacing-md;
}
</style>
