import type { RouteRecordRaw } from "vue-router";
import { createRouter, createWebHistory } from "vue-router";

const routes: RouteRecordRaw[] = [
  {
    path: "/",
    component: () => import("@/components/layout/MainLayout.vue"),
    children: [
      {
        path: "",
        redirect: "/workspace",
      },
      {
        path: "workspace",
        name: "Workspace",
        component: () => import("@/views/workspace/index.vue"),
        meta: { title: "工作台" },
      },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
});

router.beforeEach((to, _from, next) => {
  const title = to.meta.title as string;
  if (title) {
    document.title = `${title} - AI Agent`;
  }
  next();
});

export default router;
