export const PHONE_COUNTRIES = [
  { code: 'CN', dialCode: '+86',  name: '中国大陆',  flag: '🇨🇳', pattern: /^1[3-9]\d{9}$/,        placeholder: '13800138000' },
  { code: 'HK', dialCode: '+852', name: '香港',      flag: '🇭🇰', pattern: /^\d{8}$/,              placeholder: '91234567' },
  { code: 'MO', dialCode: '+853', name: '澳门',      flag: '🇲🇴', pattern: /^6\d{7}$/,             placeholder: '66123456' },
  { code: 'TW', dialCode: '+886', name: '台湾',      flag: '🇹🇼', pattern: /^09\d{8}$/,            placeholder: '0912345678' },
  { code: 'US', dialCode: '+1',   name: '美国',      flag: '🇺🇸', pattern: /^\d{10}$/,             placeholder: '2025551234' },
  { code: 'CA', dialCode: '+1',   name: '加拿大',    flag: '🇨🇦', pattern: /^\d{10}$/,             placeholder: '4165551234' },
  { code: 'GB', dialCode: '+44',  name: '英国',      flag: '🇬🇧', pattern: /^7\d{9}$/,             placeholder: '7911123456' },
  { code: 'AU', dialCode: '+61',  name: '澳大利亚',  flag: '🇦🇺', pattern: /^4\d{8}$/,             placeholder: '412345678' },
  { code: 'JP', dialCode: '+81',  name: '日本',      flag: '🇯🇵', pattern: /^0\d{9,10}$/,          placeholder: '09012345678' },
  { code: 'KR', dialCode: '+82',  name: '韩国',      flag: '🇰🇷', pattern: /^01[016789]\d{7,8}$/,  placeholder: '01012345678' },
  { code: 'SG', dialCode: '+65',  name: '新加坡',    flag: '🇸🇬', pattern: /^[89]\d{7}$/,          placeholder: '91234567' },
  { code: 'MY', dialCode: '+60',  name: '马来西亚',  flag: '🇲🇾', pattern: /^1\d{8,9}$/,           placeholder: '112345678' },
  { code: 'TH', dialCode: '+66',  name: '泰国',      flag: '🇹🇭', pattern: /^0\d{8,9}$/,           placeholder: '0812345678' },
  { code: 'VN', dialCode: '+84',  name: '越南',      flag: '🇻🇳', pattern: /^0[3-9]\d{8}$/,        placeholder: '0912345678' },
  { code: 'ID', dialCode: '+62',  name: '印度尼西亚',flag: '🇮🇩', pattern: /^08\d{8,11}$/,         placeholder: '08123456789' },
  { code: 'PH', dialCode: '+63',  name: '菲律宾',    flag: '🇵🇭', pattern: /^09\d{9}$/,            placeholder: '09171234567' },
  { code: 'IN', dialCode: '+91',  name: '印度',      flag: '🇮🇳', pattern: /^[6-9]\d{9}$/,         placeholder: '9123456789' },
  { code: 'DE', dialCode: '+49',  name: '德国',      flag: '🇩🇪', pattern: /^\d{10,11}$/,          placeholder: '15123456789' },
  { code: 'FR', dialCode: '+33',  name: '法国',      flag: '🇫🇷', pattern: /^0[67]\d{8}$/,         placeholder: '0612345678' },
  { code: 'IT', dialCode: '+39',  name: '意大利',    flag: '🇮🇹', pattern: /^3\d{9}$/,             placeholder: '3123456789' },
  { code: 'ES', dialCode: '+34',  name: '西班牙',    flag: '🇪🇸', pattern: /^[67]\d{8}$/,          placeholder: '612345678' },
  { code: 'NL', dialCode: '+31',  name: '荷兰',      flag: '🇳🇱', pattern: /^06\d{8}$/,            placeholder: '0612345678' },
  { code: 'RU', dialCode: '+7',   name: '俄罗斯',    flag: '🇷🇺', pattern: /^9\d{9}$/,             placeholder: '9123456789' },
  { code: 'BR', dialCode: '+55',  name: '巴西',      flag: '🇧🇷', pattern: /^\d{10,11}$/,          placeholder: '11912345678' },
  { code: 'AE', dialCode: '+971', name: '阿联酋',    flag: '🇦🇪', pattern: /^5\d{8}$/,             placeholder: '501234567' },
  { code: 'SA', dialCode: '+966', name: '沙特阿拉伯',flag: '🇸🇦', pattern: /^5\d{8}$/,             placeholder: '512345678' },
  { code: 'NZ', dialCode: '+64',  name: '新西兰',    flag: '🇳🇿', pattern: /^02\d{7,9}$/,          placeholder: '0211234567' },
]

export const DEFAULT_COUNTRY = PHONE_COUNTRIES[0] // 中国大陆

/**
 * 将本地号码与国家码合成 E.164 格式
 * 自动去掉本地号码开头的 0（部分国家拨打时带前缀0，E.164不需要）
 */
export function toE164(dialCode, localNumber) {
  const local = localNumber.replace(/^0+/, '').replace(/\s|-/g, '')
  return `${dialCode}${local}`
}

/**
 * 脱敏 E.164 手机号，保留国家码 + 首3位 + 末4位
 */
export function maskPhone(e164) {
  if (!e164 || !e164.startsWith('+')) return e164
  // 找到国家码长度（1-3位）
  const match = e164.match(/^(\+\d{1,3})(\d+)$/)
  if (!match) return e164
  const [, countryCode, number] = match
  if (number.length <= 7) return `${countryCode} ${'*'.repeat(number.length)}`
  const visible = number.slice(-4)
  const masked = '*'.repeat(number.length - 4)
  return `${countryCode} ${masked}${visible}`
}
