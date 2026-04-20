<template>
  <div class="product-manage-page">
    <div class="page-header">
      <h2 class="page-title">商品管理</h2>
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        添加商品
      </el-button>
    </div>

    <!-- 搜索筛选 -->
    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="关键词" class="keyword-filter-item">
          <el-input
            v-model="searchKeyword"
            class="keyword-input"
            placeholder="搜索商品名称"
            clearable
            @input="debouncedSearch"
            @keyup.enter="handleSearch"
          />
          <el-button class="keyword-search-btn" @click="handleSearch">
            <el-icon><Search /></el-icon>
          </el-button>
        </el-form-item>
        <el-form-item label="分类">
          <el-select
            v-model="selectedCategory"
            placeholder="全部分类"
            clearable
            style="width: 160px"
            @change="handleSearch"
          >
            <el-option
              v-for="cat in categories"
              :key="cat.id"
              :label="cat.name"
              :value="cat.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="selectedStatus"
            placeholder="全部状态"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option label="在售" value="ON_SALE" />
            <el-option label="已下架" value="OFF_SALE" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 商品列表 -->
    <el-card class="table-card">
      <el-table
        v-loading="loading"
        :data="products"
        stripe
        :default-sort="{ prop: 'id', order: 'descending' }"
        @sort-change="handleSortChange"
      >
        <el-table-column prop="id" label="ID" width="80" sortable="custom" />
        <el-table-column label="商品图片" width="100">
          <template #default="{ row }">
            <img
              :src="row.mainImage || '/placeholder.png'"
              :alt="row.name"
              class="product-image"
            />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="200" />
        <el-table-column prop="categoryName" label="分类" width="120" sortable="custom">
          <template #default="{ row }">
            {{ row.categoryName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="price" label="价格" width="120" align="right" sortable="custom">
          <template #default="{ row }">
            <span class="price-text">¥{{ formatPrice(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="100" align="center" sortable="custom">
          <template #default="{ row }">
            <el-tag :type="row.stock > 10 ? 'success' : row.stock > 0 ? 'warning' : 'danger'">
              {{ row.stock }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              active-text="在售"
              inactive-text="下架"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <div style="display: flex; justify-content: center; gap: 4px;">
              <el-button text type="primary" @click="handleEdit(row)">
                编辑
              </el-button>
              <el-button text type="success" @click="handleSku(row)">
                规格
              </el-button>
              <el-button text type="danger" @click="handleDelete(row.id)">
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div v-if="total > pageSize" class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchProducts"
          @current-change="fetchProducts"
        />
      </div>
    </el-card>

    <!-- 添加/编辑商品对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑商品' : '添加商品'"
      width="600px"
    >
      <el-form
        ref="formRef"
        :model="productForm"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="productForm.name" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="商品分类" prop="categoryId">
          <el-select v-model="productForm.categoryId" placeholder="请选择分类">
            <el-option
              v-for="cat in categories"
              :key="cat.id"
              :label="cat.name"
              :value="cat.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="商品价格" prop="price">
          <el-input-number
            v-model="productForm.price"
            :min="0"
            :step="0.01"
            :precision="2"
            placeholder="请输入价格"
          />
        </el-form-item>
        <el-form-item label="商品库存" prop="stock">
          <el-input-number
            v-model="productForm.stock"
            :min="0"
            placeholder="请输入库存"
          />
        </el-form-item>
        <el-form-item label="商品图片" prop="imageUrl">
          <el-input
            v-model="productForm.imageUrl"
            placeholder="请输入图片URL"
          />
          <div v-if="productForm.imageUrl" class="image-preview">
            <img :src="productForm.imageUrl" alt="预览" />
          </div>
        </el-form-item>
        <el-form-item label="商品详情" prop="detail">
          <el-input
            v-model="productForm.detail"
            type="textarea"
            :rows="4"
            placeholder="请输入商品详情"
          />
        </el-form-item>
        <el-form-item label="商品状态" prop="status">
          <el-radio-group v-model="productForm.status">
            <el-radio :value="1">在售</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- SKU 规格配置对话框 -->
    <el-dialog
      v-model="skuDialogVisible"
      :title="`规格配置 — ${skuProduct?.name || ''}`"
      width="780px"
      destroy-on-close
    >
      <SkuConfigEditor
        v-if="skuProduct"
        :product-id="skuProduct.id"
        @saved="handleSkuSaved"
      />
    </el-dialog>

    <!-- 修改库存对话框 -->
    <el-dialog v-model="stockDialogVisible" title="修改库存" width="400px">
      <el-form label-width="80px">
        <el-form-item label="当前库存">
          <span>{{ currentProduct?.stock || 0 }}</span>
        </el-form-item>
        <el-form-item label="新库存">
          <el-input-number
            v-model="newStock"
            :min="0"
            placeholder="请输入新库存"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="stockDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="handleStockSubmit"
        >
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import {
  getAllProducts,
  createProduct,
  updateProduct,
  deleteProduct,
  updateProductStatus,
  updateProductStock
} from '@/api/admin/product'
import { getAllCategories } from '@/api/category'
import { formatPrice } from '@/utils/format'
import { debounce } from '@/utils/helpers'
import { ElMessage, ElMessageBox } from 'element-plus'
import SkuConfigEditor from '@/components/admin/SkuConfigEditor.vue'

const loading = ref(false)
const products = ref([])
const categories = ref([])
const dialogVisible = ref(false)
const stockDialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const searchKeyword = ref('')
const selectedCategory = ref(null)
const selectedStatus = ref(null)
const sortBy = ref('id')
const sortDir = ref('desc')

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const currentProduct = ref(null)
const newStock = ref(0)

const skuDialogVisible = ref(false)
const skuProduct = ref(null)

// 商品表单
const productForm = ref({
  id: null,
  name: '',
  categoryId: null,
  price: 0,
  stock: 0,
  imageUrl: '',
  detail: '',
  status: 1
})

// 验证规则
const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择商品分类', trigger: 'change' }],
  price: [{ required: true, message: '请输入商品价格', trigger: 'blur' }],
  stock: [{ required: true, message: '请输入商品库存', trigger: 'blur' }],
  imageUrl: [{ required: true, message: '请输入商品图片URL', trigger: 'blur' }],
  detail: [{ required: true, message: '请输入商品详情', trigger: 'blur' }]
}

// 获取商品列表
const fetchProducts = async () => {
  loading.value = true
  try {
    const params = { page: currentPage.value, size: pageSize.value }
    if (searchKeyword.value) params.keyword = searchKeyword.value
    if (selectedCategory.value) params.categoryId = selectedCategory.value
    if (selectedStatus.value) params.status = selectedStatus.value
    params.sortBy = sortBy.value
    params.sortDir = sortDir.value
    const data = await getAllProducts(params)
    products.value = data.list
    total.value = data.total
  } catch (error) {
    console.error('获取商品列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 获取分类列表
const fetchCategories = async () => {
  try {
    categories.value = await getAllCategories()
  } catch (error) {
    console.error('获取分类列表失败:', error)
  }
}

// 搜索
const handleSearch = () => {
  currentPage.value = 1
  fetchProducts()
}

// 防抖搜索
const debouncedSearch = debounce(handleSearch, 500)

// 排序
const handleSortChange = ({ prop, order }) => {
  sortBy.value = prop || 'id'
  sortDir.value = order === 'ascending' ? 'asc' : 'desc'
  currentPage.value = 1
  fetchProducts()
}

// 添加商品
const handleAdd = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

// 编辑商品
const handleEdit = (product) => {
  isEdit.value = true
  productForm.value = {
    id: product.id,
    name: product.name,
    categoryId: product.categoryId,
    price: product.price,
    stock: product.stock,
    imageUrl: product.mainImage,  // 后端 mainImage -> 前端 imageUrl
    detail: product.detail,
    status: product.status
  }
  dialogVisible.value = true
}

// 删除商品
const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该商品吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await deleteProduct(id)
    ElMessage.success('删除成功')
    fetchProducts()
  } catch (error) {
    // 用户取消
  }
}

// 切换上下架
const handleStatusChange = async (product) => {
  try {
    const newStatus = product.status === 1 ? 0 : 1
    await updateProductStatus(product.id, newStatus)
    ElMessage.success(newStatus === 1 ? '已上架' : '已下架')
    fetchProducts()
  } catch (error) {
    console.error('修改状态失败:', error)
  }
}

// 打开 SKU 规格配置
const handleSku = (product) => {
  skuProduct.value = product
  skuDialogVisible.value = true
}

// SKU 保存成功回调
const handleSkuSaved = (result) => {
  // 刷新商品列表以更新 hasSku 等字段
  fetchProducts()

  if (result?.action === 'save') {
    skuDialogVisible.value = false
    skuProduct.value = null
  }
}

// 修改库存
const handleStock = (product) => {
  currentProduct.value = product
  newStock.value = product.stock
  stockDialogVisible.value = true
}

// 提交库存修改
const handleStockSubmit = async () => {
  submitting.value = true
  try {
    await updateProductStock(currentProduct.value.id, newStock.value)
    ElMessage.success('库存修改成功')
    stockDialogVisible.value = false
    fetchProducts()
  } catch (error) {
    console.error('修改库存失败:', error)
  } finally {
    submitting.value = false
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()

    submitting.value = true

    // 字段映射：前端 imageUrl -> 后端 mainImage
    const data = { ...productForm.value }
    delete data.id
    data.mainImage = data.imageUrl
    delete data.imageUrl

    if (isEdit.value) {
      await updateProduct(productForm.value.id, data)
      ElMessage.success('修改成功')
    } else {
      await createProduct(data)
      ElMessage.success('添加成功')
    }

    dialogVisible.value = false
    fetchProducts()
  } catch (error) {
    if (error !== false) {
      console.error('提交失败:', error)
    }
  } finally {
    submitting.value = false
  }
}

// 重置表单
const resetForm = () => {
  productForm.value = {
    id: null,
    name: '',
    categoryId: null,
    price: 0,
    stock: 0,
    imageUrl: '',
    detail: '',
    status: 1
  }

  if (formRef.value) {
    formRef.value.resetFields()
  }
}

onMounted(() => {
  fetchProducts()
  fetchCategories()
})
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.product-manage-page {
  padding: $spacing-lg;

  .page-header {
    @include flex-between;
    margin-bottom: $spacing-lg;

    .page-title {
      font-size: 24px;
      color: $text-primary;
      margin: 0;
    }
  }

  .filter-card {
    margin-bottom: $spacing-lg;

    .keyword-filter-item {
      :deep(.el-form-item__content) {
        display: flex;
        align-items: center;
        gap: 0;
      }

      .keyword-input {
        width: 240px;
      }

      .keyword-search-btn {
        padding: 0 14px;
        margin-left: -1px;
      }
    }
  }

  .table-card {
    .pagination {
      margin-top: $spacing-lg;
      display: flex;
      justify-content: flex-end;
    }

    .product-image {
      width: 60px;
      height: 60px;
      object-fit: cover;
      border-radius: $border-radius-base;
    }

    .price-text {
      color: $danger-color;
      font-weight: 500;
    }
  }

  .image-preview {
    margin-top: $spacing-sm;

    img {
      max-width: 200px;
      max-height: 200px;
      border-radius: $border-radius-base;
    }
  }
}
</style>
