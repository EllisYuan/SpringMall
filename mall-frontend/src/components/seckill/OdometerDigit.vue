<template>
  <!-- 单个数字位：内部 0-9 纵向条带，按 value 上下滚动（老虎机效果） -->
  <span class="odo-digit" :class="{ 'odo-digit--ms': ms }">
    <span
      class="odo-strip"
      :style="{
        transform: `translateY(-${value * 10}%)`,
        transitionDuration: fast ? '0.12s' : '0.3s'
      }"
    >
      <span v-for="n in 10" :key="n - 1" class="odo-cell">{{ n - 1 }}</span>
    </span>
  </span>
</template>

<script setup>
defineProps({
  // 当前数字 0-9
  value: { type: Number, default: 0 },
  // 快速滚动（秒/毫秒位用更短过渡）
  fast: { type: Boolean, default: false },
  // 毫秒位样式（稍小、强调色）
  ms: { type: Boolean, default: false }
})
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.odo-digit {
  display: inline-block;
  width: 0.66em;
  height: 1.25em;
  overflow: hidden;
  vertical-align: bottom;
  line-height: 1.25em;
  text-align: center;

  &--ms {
    color: #ffd54a; // 毫秒位高亮，强化飞速跳动的紧迫感
  }
}

.odo-strip {
  display: flex;
  flex-direction: column;
  transition-property: transform;
  transition-timing-function: cubic-bezier(0.25, 1, 0.5, 1);
  will-change: transform;
}

.odo-cell {
  height: 1.25em;
  display: flex;
  align-items: center;
  justify-content: center;
  font-variant-numeric: tabular-nums;
}
</style>
