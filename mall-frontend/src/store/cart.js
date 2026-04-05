/**
 * 购物车状态管理
 */
import { defineStore } from 'pinia'
import {
  getCart as getCartApi,
  addToCart as addToCartApi,
  updateCartItem as updateCartItemApi,
  deleteCartItem as deleteCartItemApi,
  checkCartItem as checkCartItemApi,
  checkAllCart as checkAllCartApi
} from '@/api/cart'
import { ElMessage } from 'element-plus'

export const useCartStore = defineStore('cart', {
  state: () => ({
    items: [],
    loading: false
  }),

  getters: {
    /**
     * 购物车商品数量
     */
    cartCount: (state) => {
      return state.items.reduce((total, item) => total + item.quantity, 0)
    },

    /**
     * 已选中的商品
     */
    checkedItems: (state) => {
      return state.items.filter(item => item.checked)
    },

    /**
     * 已选中商品的数量
     */
    checkedCount: (state) => {
      return state.items.filter(item => item.checked).reduce((total, item) => total + item.quantity, 0)
    },

    /**
     * 已选中商品的总价
     */
    checkedTotal: (state) => {
      return state.items
        .filter(item => item.checked)
        .reduce((total, item) => {
          return total + (item.productPrice || 0) * item.quantity
        }, 0)
    },

    /**
     * 是否全选
     */
    isAllChecked: (state) => {
      return state.items.length > 0 && state.items.every(item => item.checked)
    }
  },

  actions: {
    /**
     * 获取购物车列表
     */
    async fetchCart() {
      try {
        this.loading = true
        const data = await getCartApi()
        // 将后端返回的 checked 字段（0/1 整数）统一转换为布尔值
        this.items = (data || []).map(item => ({ ...item, checked: !!item.checked }))
      } catch (error) {
        console.error('获取购物车失败:', error)
        this.items = []
      } finally {
        this.loading = false
      }
    },

    /**
     * 添加商品到购物车
     * @param {number} productId - 商品 ID
     * @param {number} quantity - 数量
     * @param {number|undefined} skuId - SKU ID（有 SKU 商品必传）
     */
    async addItem(productId, quantity = 1, skuId = undefined) {
      try {
        const payload = { productId, quantity }
        if (skuId !== undefined) payload.skuId = skuId
        await addToCartApi(payload)
        // 重新获取购物车数据
        await this.fetchCart()
        ElMessage.success('已加入购物车')
      } catch (error) {
        ElMessage.error('加入购物车失败，请重试')
        console.error('添加购物车失败:', error)
        throw error
      }
    },

    /**
     * 更新购物车商品数量
     * @param {number} id - 购物车项 ID
     * @param {number} quantity - 数量
     */
    async updateQuantity(id, quantity) {
      try {
        await updateCartItemApi(id, quantity)

        // 更新本地状态
        const item = this.items.find(item => item.id === id)
        if (item) {
          item.quantity = quantity
        }
      } catch (error) {
        console.error('更新数量失败:', error)
        throw error
      }
    },

    /**
     * 删除购物车商品
     * @param {number} id - 购物车项 ID
     */
    async removeItem(id) {
      try {
        await deleteCartItemApi(id)
        ElMessage.success('已删除')

        // 更新本地状态
        this.items = this.items.filter(item => item.id !== id)
      } catch (error) {
        console.error('删除商品失败:', error)
        throw error
      }
    },

    /**
     * 切换商品选中状态
     * @param {number} id - 购物车项 ID
     * @param {boolean} checked - 是否选中
     */
    async toggleCheck(id, checked) {
      try {
        // 将boolean转换为0/1
        const checkedValue = checked ? 1 : 0
        await checkCartItemApi(id, checkedValue)

        // 更新本地状态
        const item = this.items.find(item => item.id === id)
        if (item) {
          item.checked = checked
        }
      } catch (error) {
        console.error('切换选中状态失败:', error)
        throw error
      }
    },

    /**
     * 全选/取消全选
     * @param {boolean} checked - 是否选中
     */
    async toggleCheckAll(checked) {
      try {
        // 将boolean转换为0/1
        const checkedValue = checked ? 1 : 0
        await checkAllCartApi(checkedValue)

        // 更新本地状态
        this.items.forEach(item => {
          item.checked = checked
        })
      } catch (error) {
        console.error('全选操作失败:', error)
        throw error
      }
    },

    /**
     * 清空购物车（本地状态）
     */
    clearCart() {
      this.items = []
    }
  }
})
