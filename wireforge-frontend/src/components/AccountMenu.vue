<template>
  <button v-if="!user" type="button" class="wf-account-login wf-auth" @click="emit('login')">
    <UserRound :size="15" aria-hidden="true" />
    飞书登录
  </button>
  <el-dropdown v-else trigger="click" placement="bottom-end" popper-class="wf-account-popper wf-auth" @command="emit('command', $event)">
    <button type="button" class="wf-account-trigger wf-auth" :title="`当前登录：${user.name}（${roleDescription}）`" :aria-label="`${user.name}，账号菜单`">
      <AccountAvatar :name="user.name" :src="user.avatar" />
      <span class="wf-account-name">{{ user.name }}</span>
      <ChevronDown :size="13" aria-hidden="true" />
    </button>
    <template #dropdown>
      <el-dropdown-menu class="wf-account-menu">
        <el-dropdown-item disabled class="wf-account-identity">
          <strong>{{ user.name }}</strong>
          <span>{{ roleDescription }}</span>
        </el-dropdown-item>
        <el-dropdown-item v-if="canManageMembers" command="open-members">
          <Users :size="15" aria-hidden="true" />项目成员
        </el-dropdown-item>
        <el-dropdown-item v-if="canViewAudit" command="open-audit-log">
          <ScrollText :size="15" aria-hidden="true" />操作日志
        </el-dropdown-item>
        <el-dropdown-item command="copy-open-id">
          <Copy :size="15" aria-hidden="true" />复制飞书编号
        </el-dropdown-item>
        <el-dropdown-item command="switch-account">
          <Repeat2 :size="15" aria-hidden="true" />切换账号
        </el-dropdown-item>
        <el-dropdown-item command="logout" divided class="wf-account-logout">
          <LogOut :size="15" aria-hidden="true" />退出登录
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { ChevronDown, Copy, LogOut, Repeat2, ScrollText, UserRound, Users } from 'lucide-vue-next'
import type { AuthUser } from '../services/auth'
import AccountAvatar from './AccountAvatar.vue'

withDefaults(defineProps<{
  user: AuthUser | null
  roleDescription: string
  canManageMembers?: boolean
  canViewAudit?: boolean
}>(), { canManageMembers: false, canViewAudit: false })

const emit = defineEmits<{
  (e: 'login'): void
  (e: 'command', command: string): void
}>()
</script>

<style scoped>
.wf-account-login,
.wf-account-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 36px;
  padding: 4px 6px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  font-size: 13px;
  line-height: 20px;
  cursor: pointer;
  transition: background-color 0.15s;
}
.wf-account-login { padding: 6px 10px; color: var(--wf-auth-blue); white-space: nowrap; }
.wf-account-trigger:hover,
.wf-account-login:hover { background: var(--wf-bg); }
.wf-account-trigger:focus-visible,
.wf-account-login:focus-visible { outline: 2px solid var(--wf-auth-blue); outline-offset: 2px; }
.wf-account-name { max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wf-account-trigger > svg { flex: 0 0 auto; color: var(--wf-text-muted); }
</style>

<style>
.wf-account-popper.el-popper {
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--wf-border);
  border-radius: 11px;
  background: var(--wf-surface);
  box-shadow: 0 8px 24px rgb(15 23 42 / 10%);
}
.wf-account-popper .wf-account-menu.el-dropdown-menu {
  width: min(252px, calc(100vw - 24px));
  padding: 5px;
  border: 0 !important;
  background: var(--wf-surface) !important;
}
.wf-account-popper .el-dropdown-menu__item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 36px;
  padding: 8px 10px;
  border-radius: 6px;
  color: var(--wf-text-main) !important;
  font-size: 13px;
  line-height: 20px;
}
.wf-account-popper .el-dropdown-menu__item > svg { flex: 0 0 auto; margin: 0; color: var(--wf-text-muted); }
.wf-account-popper .el-dropdown-menu__item:not(.is-disabled):is(:hover, :focus) {
  background: var(--wf-bg) !important;
  color: var(--wf-text-main) !important;
}
.wf-account-popper .wf-account-identity.el-dropdown-menu__item {
  display: block;
  margin-bottom: 4px;
  padding: 10px 10px 12px;
  border-bottom: 1px solid var(--wf-border);
  border-radius: 0;
  cursor: default;
  white-space: normal;
  overflow-wrap: anywhere;
}
.wf-account-identity strong { display: block; font-weight: 500; }
.wf-account-identity span { display: block; margin-top: 3px; color: var(--wf-text-muted); font-size: 11px; line-height: 17px; }
.wf-account-popper .wf-account-logout.el-dropdown-menu__item,
.wf-account-popper .wf-account-logout.el-dropdown-menu__item:is(:hover, :focus),
.wf-account-popper .wf-account-logout.el-dropdown-menu__item > svg { color: var(--wf-auth-danger) !important; }
.wf-account-popper .el-dropdown-menu__item--divided { margin-top: 4px; border-color: var(--wf-border); }
.wf-account-popper .el-dropdown-menu__item--divided::before { display: none; }
</style>
