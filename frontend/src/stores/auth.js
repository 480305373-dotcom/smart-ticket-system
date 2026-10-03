import { defineStore } from "pinia";
function restore() {
  try {
    const session = JSON.parse(
      localStorage.getItem("smartticket-session") || "null",
    );
    if (
      !session?.token ||
      !session.username ||
      !["USER", "ADMIN"].includes(session.role)
    )
      return null;
    const payload = JSON.parse(
      atob(session.token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")),
    );
    return payload.exp * 1000 > Date.now() ? session : null;
  } catch {
    return null;
  }
}
export const useAuth = defineStore("auth", {
  state: () => ({ session: restore() }),
  getters: {
    token: (s) => s.session?.token,
    userId: (s) => s.session?.userId,
    username: (s) => s.session?.username,
    role: (s) => s.session?.role,
    isAdmin: (s) => s.session?.role === "ADMIN",
  },
  actions: {
    set(session) {
      this.session = session;
      localStorage.setItem("smartticket-session", JSON.stringify(session));
    },
    clear() {
      this.session = null;
      localStorage.removeItem("smartticket-session");
    },
  },
});
