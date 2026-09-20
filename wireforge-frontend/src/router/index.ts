import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'projects', component: () => import('../views/ProjectList.vue') },
    { path: '/projects/:id', name: 'project-detail', component: () => import('../views/ProjectDetail.vue') },
    { path: '/projects/:id/prototype', name: 'prototype', component: () => import('../views/PrototypeView.vue') },
    { path: '/projects/:id/preview', name: 'PurePreview', component: () => import('@/views/PurePreviewView.vue') },
  ],
})

export default router