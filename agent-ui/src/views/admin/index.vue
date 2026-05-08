<template>
  <div class="admin">
    <el-container>
      <el-aside width="200px">
        <el-menu>
          <el-menu-item index="1">
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="2">
            <span>Skills 管理</span>
          </el-menu-item>
          <el-menu-item index="3">
            <span>监控面板</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main>
        <el-empty description="管理后台开发中..." />
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts"></script>

<style scoped>
.admin {
  width: 100%;
  height: 100vh;
}

.el-aside {
  background-color: var(--bg-admin-aside);
  color: var(--color-admin-aside);
}
</style>
