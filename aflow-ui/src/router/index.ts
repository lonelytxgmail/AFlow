import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/definitions'
  },
  {
    path: '/definitions',
    name: 'DefinitionList',
    component: () => import('../views/DefinitionList.vue')
  },
  {
    path: '/definitions/new',
    name: 'DefinitionCreate',
    component: () => import('../views/DefinitionEdit.vue')
  },
  {
    path: '/definitions/:id',
    name: 'DefinitionEdit',
    component: () => import('../views/DefinitionEdit.vue')
  },
  {
    path: '/instances',
    name: 'InstanceList',
    component: () => import('../views/InstanceList.vue')
  },
  {
    path: '/instances/:id',
    name: 'InstanceDetail',
    component: () => import('../views/InstanceDetail.vue')
  },
  {
    path: '/atomic-components',
    name: 'AtomicComponents',
    component: () => import('../views/AtomicComponents.vue')
  },
  {
    path: '/monitoring',
    name: 'Monitoring',
    component: () => import('../views/Monitoring.vue'),
    meta: { title: '监控' }
  },
  {
    path: '/approvals',
    name: 'Approvals',
    component: () => import('../views/Approvals.vue'),
    meta: { title: '审批' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
