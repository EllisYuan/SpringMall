import request from '../request'

export const getSkuConfig = (productId) => {
  return request({ url: `/admin/products/${productId}/sku-config`, method: 'GET' })
}

export const saveSkuConfig = (productId, data) => {
  return request({ url: `/admin/products/${productId}/sku-config`, method: 'PUT', data })
}

export const deleteSkuConfig = (productId) => {
  return request({ url: `/admin/products/${productId}/sku-config`, method: 'DELETE' })
}
