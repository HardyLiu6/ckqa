<template>
  <component :is="$route.meta.layoutComponent || 'main'" class="app-layout">
    <RouterView />
  </component>
</template>
