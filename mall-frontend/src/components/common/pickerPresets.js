/**
 * 通用查询选择器（EntityPicker）的实体预设配置
 *
 * 每个预设描述「调哪个 API、展示哪些列、用哪个字段做值/标签」，
 * 配合 EntityPickerDialog / EntityPickerField 使用。
 * 新增一种可选实体，只需在此再写一份配置即可复用整套查询弹窗。
 *
 * 配置约定：
 *   title            弹窗标题
 *   searchPlaceholder搜索框占位
 *   valueField       回填的值字段（通常 'id'）
 *   labelField       展示用的标签字段（如 'name' / 'username'）
 *   fetchList(params)  关键词分页查询，返回 { list, total }（或数组）
 *   fetchById(id)      可选，按 id 取详情用于编辑态回显
 *   columns          表格列：{ prop, label, width?, minWidth?, type?, statusMap? }
 *                    type: 'image' | 'price' | 'status' | 'text'(默认)
 *   rowDisabled(row)   可选，返回 true 则该行置灰不可选
 *   disabledTip        可选，行被禁用时「操作」列展示的说明文字
 */
import { getAllProducts, getProductById } from '@/api/admin/product'
import { getAllUsers } from '@/api/admin/user'
import { PRODUCT_STATUS } from '@/utils/constants'

/** 商品选择器（管理端） */
export const productPicker = {
  title: '选择商品',
  searchPlaceholder: '输入商品名称搜索',
  valueField: 'id',
  labelField: 'name',
  fetchList: (params) => getAllProducts(params),
  fetchById: (id) => getProductById(id),
  columns: [
    { prop: 'mainImage', label: '图片', type: 'image', width: 70 },
    { prop: 'name', label: '商品名称', type: 'text', minWidth: 200 },
    { prop: 'price', label: '价格', type: 'price', width: 100 },
    { prop: 'stock', label: '库存', type: 'text', width: 80 },
    {
      prop: 'status',
      label: '状态',
      type: 'status',
      width: 80,
      statusMap: { 0: { text: '下架', type: 'info' }, 1: { text: '上架', type: 'success' } }
    }
  ]
}

/**
 * 上架商品选择器（管理端）
 *
 * 基于 productPicker，仅列出上架商品（后端 status 参数取值为枚举名 'ON_SALE'/'OFF_SALE'，非 0/1）。
 * 用于秒杀活动等只能挂上架商品的场景，避免选完提交才被后端 PRODUCT_UNAVAILABLE 拒绝。
 * 库存为 0 的商品虽然上架，但会撞后端 SECKILL_STOCK_EXCEEDED，故一并置灰。
 */
export const onSaleProductPicker = {
  ...productPicker,
  title: '选择商品（仅上架）',
  fetchList: (params) => getAllProducts({ ...params, status: PRODUCT_STATUS.ON_SALE }),
  rowDisabled: (row) => !row.stock,
  disabledTip: '无库存'
}

/** 用户选择器（管理端）—— 第二个示例，证明换实体只需换配置 */
export const userPicker = {
  title: '选择用户',
  searchPlaceholder: '输入用户名/邮箱搜索',
  valueField: 'id',
  labelField: 'username',
  fetchList: (params) => getAllUsers(params),
  fetchById: null,
  columns: [
    { prop: 'username', label: '用户名', type: 'text', minWidth: 140 },
    { prop: 'email', label: '邮箱', type: 'text', minWidth: 200 },
    {
      prop: 'status',
      label: '状态',
      type: 'status',
      width: 80,
      statusMap: { 0: { text: '禁用', type: 'danger' }, 1: { text: '正常', type: 'success' } }
    }
  ]
}
