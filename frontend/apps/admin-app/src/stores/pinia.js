import { createPinia } from 'pinia'

export const adminPinia = createPinia()

export function getAdminPinia() {
  return adminPinia
}
