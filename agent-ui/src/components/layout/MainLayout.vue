<template>
  <div class="main-layout">
    <SideBar />
    <div class="main-content">
      <ChatArea
        :right-panel-visible="rightPanelVisible"
        @toggle-right-panel="rightPanelVisible = !rightPanelVisible"
      />
    </div>
    <RightPanel :visible="rightPanelVisible" @close="rightPanelVisible = false" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import SideBar from '@/components/layout/SideBar.vue'
import ChatArea from '@/components/chat/ChatArea.vue'
import RightPanel from '@/components/chat/RightPanel.vue'

const rightPanelVisible = ref(true)
</script>

<style scoped>
.main-layout {
  display: flex;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.main-content {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}
</style>
