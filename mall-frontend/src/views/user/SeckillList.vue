<template>
  <div class="seckill-list-page">
    <div class="container">
      <!-- 页面标题 -->
      <div class="page-header">
        <h1 class="page-title">限时秒杀</h1>
        <p class="page-sub">好物限量，先到先得</p>
      </div>

      <Loading v-if="seckillStore.listLoading" />
      <Empty v-else-if="!seckillStore.activities.length" type="order" text="暂无秒杀活动" />

      <div v-else class="activity-sections">
        <!-- 进行中 -->
        <template v-if="seckillStore.activeActivities.length">
          <h2 class="section-label">
            <span class="dot dot--active"></span>进行中
          </h2>
          <div class="activity-grid">
            <ActivityCard
              v-for="activity in seckillStore.activeActivities"
              :key="activity.id"
              :activity="activity"
              @click="goToDetail(activity.id)"
            />
          </div>
        </template>

        <!-- 即将开始 -->
        <template v-if="seckillStore.upcomingActivities.length">
          <h2 class="section-label">
            <span class="dot dot--upcoming"></span>即将开始
          </h2>
          <div class="activity-grid">
            <ActivityCard
              v-for="activity in seckillStore.upcomingActivities"
              :key="activity.id"
              :activity="activity"
              @click="goToDetail(activity.id)"
            />
          </div>
        </template>

        <!-- 已结束 / 售罄 -->
        <template v-if="endedActivities.length">
          <h2 class="section-label section-label--muted">
            <span class="dot dot--ended"></span>已结束
          </h2>
          <div class="activity-grid activity-grid--ended">
            <ActivityCard
              v-for="activity in endedActivities"
              :key="activity.id"
              :activity="activity"
              @click="goToDetail(activity.id)"
            />
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSeckillStore } from '@/store/seckill'
import Loading from '@/components/common/Loading.vue'
import Empty from '@/components/common/Empty.vue'
import ActivityCard from '@/components/seckill/ActivityCard.vue'

const router = useRouter()
const seckillStore = useSeckillStore()

const endedActivities = computed(() =>
  seckillStore.activities.filter(
    a => a.activityStatus === 'ENDED' || a.activityStatus === 'SOLD_OUT'
  )
)

const goToDetail = (id) => router.push(`/seckills/${id}`)

onMounted(() => seckillStore.fetchActivities())
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.seckill-list-page {
  padding: $spacing-xl 0 $spacing-xxl;
  min-height: 60vh;
}

.page-header {
  margin-bottom: $spacing-xl;
}

.page-title {
  font-size: $font-size-xl;
  font-weight: $font-weight-bold;
  color: $text-primary;
  letter-spacing: -0.02em;
  margin-bottom: $spacing-xs;
}

.page-sub {
  font-size: $font-size-sm;
  color: $text-secondary;
}

.section-label {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  font-size: $font-size-base;
  font-weight: $font-weight-bold;
  color: $text-primary;
  letter-spacing: 0.02em;
  margin: $spacing-xl 0 $spacing-md;

  &--muted { color: $text-secondary; }
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;

  &--active {
    background: $success-color;
    box-shadow: 0 0 0 3px rgba(45, 119, 56, 0.2);
    animation: pulse 2s infinite;
  }
  &--upcoming { background: $warning-color; }
  &--ended { background: $border-base; }
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 3px rgba(45, 119, 56, 0.2); }
  50% { box-shadow: 0 0 0 6px rgba(45, 119, 56, 0.08); }
}

.activity-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  background: $border-light;
  border: 1px solid $border-light;
  margin-bottom: $spacing-xl;

  @include tablet { grid-template-columns: repeat(3, 1fr); }
  @include mobile { grid-template-columns: repeat(2, 1fr); }

  &--ended {
    opacity: 0.6;
  }
}
</style>
