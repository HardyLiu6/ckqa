<template>
  <component :is="$route.meta.layoutComponent || 'main'" class="app-layout">
    <RouterView v-slot="{ Component }">
      <Transition name="page-fade" mode="out-in">
        <component :is="Component" :key="$route.path" />
      </Transition>
    </RouterView>
  </component>
</template>
