import { defineStore } from "pinia";
import { computed, ref } from "vue";

export interface UserInfo {
  id: number;
  username: string;
  nickname: string;
  avatar: string;
  email: string;
  role: string;
}

export const useUserStore = defineStore("user", () => {
  const token = ref<string>("default-token");
  const userInfo = ref<UserInfo | null>({
    id: 1,
    username: "default_user",
    nickname: "默认用户",
    avatar: "",
    email: "user@example.com",
    role: "user",
  });

  const isLoggedIn = computed(() => true);
  const displayName = computed(
    () => userInfo.value?.nickname || userInfo.value?.username || "用户",
  );

  function setToken(newToken: string) {
    token.value = newToken;
  }

  function setUserInfo(info: UserInfo) {
    userInfo.value = info;
  }

  function logout() {
    token.value = "";
    userInfo.value = null;
  }

  function init() {}

  return {
    token,
    userInfo,
    isLoggedIn,
    displayName,
    setToken,
    setUserInfo,
    logout,
    init,
  };
});
