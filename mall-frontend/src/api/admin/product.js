/**
 * 管理端 - 商品管理 API
 */
import request from '../request'

/**
 * 获取所有商品（支持分页）
 * @param {Object} params - 查询参数 { page, size, keyword, categoryId, status }
 */
export const getAllProducts = (params) => {
  return request({
    url: '/admin/products',
    method: 'GET',
    params
  })
}

/**
 * 获取商品详情（管理端，可取下架商品，含 hasSku/skuList）
 * @param {number} id - 商品 ID
 */
export const getProductById = (id) => {
  return request({
    url: `/admin/products/${id}`,
    method: 'GET'
  })
}

/**
 * 创建商品
 * @param {Object} data - 商品信息
 */
export const createProduct = (data) => {
  return request({
    url: '/admin/products',
    method: 'POST',
    data
  })
}

/**
 * 更新商品
 * @param {number} id - 商品 ID
 * @param {Object} data - 商品信息
 */
export const updateProduct = (id, data) => {
  return request({
    url: `/admin/products/${id}`,
    method: 'PUT',
    data
  })
}

/**
 * 删除商品
 * @param {number} id - 商品 ID
 */
export const deleteProduct = (id) => {
  return request({
    url: `/admin/products/${id}`,
    method: 'DELETE'
  })
}

/**
 * 更新商品状态（上下架）
 * @param {number} id - 商品 ID
 * @param {number} status - 状态（0-下架，1-上架）
 */
export const updateProductStatus = (id, status) => {
  return request({
    url: `/admin/products/${id}/status`,
    method: 'PUT',
    params: { status }
  })
}

/**
 * 更新商品库存
 * @param {number} id - 商品 ID
 * @param {number} stock - 新库存
 */
export const updateProductStock = (id, stock) => {
  return request({
    url: `/admin/products/${id}/stock`,
    method: 'PUT',
    params: { stock }
  })
}
