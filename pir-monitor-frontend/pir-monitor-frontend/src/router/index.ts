import { createRouter, createWebHistory } from 'vue-router'
import UnifiedApp from '@/views/unified/UnifiedApp.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: UnifiedApp,
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
})

export default router
