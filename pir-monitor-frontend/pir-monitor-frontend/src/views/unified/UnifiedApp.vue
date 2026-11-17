<template>
  <AuthPanel v-if="!authStore.isLoggedIn" @login-success="onLoginSuccess" />
  <ConsoleLayout v-else />
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import AuthPanel from './AuthPanel.vue'
import ConsoleLayout from './ConsoleLayout.vue'

const authStore = useAuthStore()

function onLoginSuccess() {
  // auth store already updated by AuthPanel
  // Just trigger reactivity
}
</script>
