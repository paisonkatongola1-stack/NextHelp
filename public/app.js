import { initializeApp } from "https://www.gstatic.com/firebasejs/12.17.1/firebase-app.js";
import {
  getAuth,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  sendPasswordResetEmail,
  signInWithPopup,
  GoogleAuthProvider,
  signOut,
  updateProfile
} from "https://www.gstatic.com/firebasejs/12.17.1/firebase-auth.js";
import {
  initializeFirestore,
  getFirestore,
  collection,
  doc,
  getDoc,
  setDoc,
  updateDoc,
  addDoc,
  deleteField,
  query,
  where,
  orderBy,
  limit as fsLimit,
  onSnapshot,
  collectionGroup
} from "https://www.gstatic.com/firebasejs/12.17.1/firebase-firestore.js";

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

const STATUSES = ["OPEN", "ASSIGNED", "IN_PROGRESS", "WAITING_FOR_USER", "RESOLVED", "CLOSED", "REOPENED"];
const PRIORITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];
const CATEGORIES = ["Technical", "Billing", "Account", "Other"];
const AGENT_ROLES = new Set(["SUPPORT_AGENT", "SUPPORT_MANAGER", "ADMIN"]);
const ONE_DAY_MS = 24 * 60 * 60 * 1000;
const COMMENT_FRESHNESS_MS = 3 * ONE_DAY_MS;
const CONVERSATION_WINDOW_MS = 7 * ONE_DAY_MS;

const state = {
  ready: false,
  auth: null,
  db: null,
  fbUser: null,
  profile: null,
  tickets: [],
  ticketsLoading: true,
  latestComments: new Map(),
  route: { name: "home" },
  authMode: "login",
  authError: "",
  authBusy: false,
  ticketFilter: "ALL",
  ticketSearch: "",
  detail: null,
  chatText: "",
  attachment: null,
  sending: false,
  statusBusy: false
};

let unsubscribeTickets = null;
let unsubscribeCommentsCG = null;
let unsubscribeProfile = null;
let unsubscribeDetailComments = null;

const svg = (path) =>
  `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${path}</svg>`;

const ICONS = {
  home: svg('<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/><path d="M9 21v-6h6v6"/>'),
  ticket: svg('<path d="M4 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v3a2 2 0 0 0 0 4v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-3a2 2 0 0 0 0-4z"/><path d="M13 5v14" stroke-dasharray="2 3"/>'),
  bell: svg('<path d="M18 8a6 6 0 1 0-12 0c0 7-3 8-3 8h18s-3-1-3-8"/><path d="M10.3 21a2 2 0 0 0 3.4 0"/>'),
  user: svg('<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.6-6 8-6s8 2 8 6"/>'),
  plus: svg('<path d="M12 5v14M5 12h14"/>'),
  back: svg('<path d="M15 18l-6-6 6-6"/>'),
  clip: svg('<path d="m21.4 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1 18 8.84l-8.59 8.57a2 2 0 0 1-2.83-2.83l8.49-8.48"/>'),
  send: svg('<path d="m22 2-7 20-4-9-9-4z"/><path d="M22 2 11 13"/>'),
  camera: svg('<path d="M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3z"/><circle cx="12" cy="13" r="3"/>'),
  logout: svg('<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5"/><path d="M21 12H9"/>'),
  inbox: svg('<path d="M22 12h-6l-2 3h-4l-2-3H2"/><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/>'),
  clock: svg('<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 3"/>'),
  check: svg('<path d="M20 6 9 17l-5-5"/>'),
  alert: svg('<path d="M12 9v4"/><path d="M12 17h.01"/><circle cx="12" cy="12" r="9"/>')
};

function esc(s) {
  return String(s ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function toast(msg, isErr = false) {
  const host = $("#toast-host");
  if (!host) return;
  const el = document.createElement("div");
  el.className = "toast" + (isErr ? " err" : "");
  el.textContent = msg;
  host.appendChild(el);
  setTimeout(() => el.remove(), 3200);
}

function timeAgo(ts) {
  const diff = Date.now() - ts;
  if (diff < 60e3) return "now";
  if (diff < 3600e3) return Math.floor(diff / 60e3) + "m";
  if (diff < 86400e3) return Math.floor(diff / 3600e3) + "h";
  if (diff < 7 * 86400e3) return Math.floor(diff / 86400e3) + "d";
  return new Date(ts).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function dayGroupLabel(ts) {
  const now = new Date();
  const then = new Date(ts);
  const startOfDay = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const days = Math.round((startOfDay(now) - startOfDay(then)) / ONE_DAY_MS);
  if (days <= 0) return "Today";
  if (days === 1) return "Yesterday";
  if (days < 7) return "This week";
  return then.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
}

function fmtDateTime(ts) {
  return new Date(ts).toLocaleString(undefined, {
    month: "short", day: "numeric", year: "numeric", hour: "numeric", minute: "2-digit"
  });
}

function statusLabel(s) {
  return String(s || "").toLowerCase().split("_").map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(" ");
}

function chipClass(status) {
  return String(status || "").toLowerCase();
}

const isAgent = () => state.profile && AGENT_ROLES.has(state.profile.role);
const roleLabel = () => {
  switch (state.profile?.role) {
    case "ADMIN": return "Administrator";
    case "SUPPORT_MANAGER": return "Support Manager";
    case "SUPPORT_AGENT": return "Support Agent";
    default: return "Member";
  }
};

function lastSeenKey() {
  return "nexthelp.lastSeen." + (state.fbUser?.uid || "");
}

function notifPrefKey() {
  return "nexthelp.notifPrefs." + (state.fbUser?.uid || "");
}

function notifPrefs() {
  try {
    return { status: true, comments: true, priority: true, ...JSON.parse(localStorage.getItem(notifPrefKey()) || "{}") };
  } catch (_) {
    return { status: true, comments: true, priority: true };
  }
}

function setNotifPref(key, val) {
  const prefs = notifPrefs();
  prefs[key] = val;
  localStorage.setItem(notifPrefKey(), JSON.stringify(prefs));
}

function lastSeen() {
  return Number(localStorage.getItem(lastSeenKey()) || 0);
}

async function compressImage(file, maxDim, quality) {
  const url = URL.createObjectURL(file);
  try {
    const img = new Image();
    img.src = url;
    await img.decode();
    const scale = Math.min(1, maxDim / Math.max(img.naturalWidth, img.naturalHeight));
    const w = Math.max(1, Math.round(img.naturalWidth * scale));
    const h = Math.max(1, Math.round(img.naturalHeight * scale));
    const canvas = document.createElement("canvas");
    canvas.width = w;
    canvas.height = h;
    canvas.getContext("2d").drawImage(img, 0, 0, w, h);
    return canvas.toDataURL("image/jpeg", quality);
  } finally {
    URL.revokeObjectURL(url);
  }
}

async function loadConfig() {
  if (window.__NEXTHELP_FIREBASE_CONFIG__?.apiKey && window.__NEXTHELP_FIREBASE_CONFIG__?.projectId) {
    return window.__NEXTHELP_FIREBASE_CONFIG__;
  }
  try {
    const res = await fetch("/__/firebase/init.json");
    const cfg = await res.json();
    if (cfg?.apiKey && cfg?.projectId) return cfg;
  } catch (_) {}
  return null;
}

function renderConfigError(root) {
  root.innerHTML = `
    <div class="auth-wrap">
      <div class="auth-card">
        <div class="auth-logo">
          <img src="logo.png" alt="NextHelp">
          <h1>Setup needed</h1>
          <p>No Firebase configuration found for the web app.</p>
        </div>
        <p class="muted" style="font-size:13.5px;line-height:1.6;text-align:center">
          Either deploy through Firebase Hosting (<code>firebase deploy --only hosting</code>)
          or create <code>public/firebase-config.js</code> with your web SDK config:
        </p>
        <pre class="card muted" style="font-size:12px;margin-top:14px;overflow-x:auto">window.__NEXTHELP_FIREBASE_CONFIG__ = {
  apiKey: "...",
  authDomain: "...",
  projectId: "..."
};</pre>
      </div>
    </div>`;
}

function friendlyAuthError(e) {
  const code = e?.code || "";
  const map = {
    "auth/invalid-email": "That email address doesn't look right.",
    "auth/user-not-found": "No account found with that email.",
    "auth/wrong-password": "Incorrect password. Try again.",
    "auth/invalid-credential": "Incorrect email or password.",
    "auth/email-already-in-use": "An account with that email already exists.",
    "auth/weak-password": "Password must be at least 6 characters.",
    "auth/too-many-requests": "Too many attempts. Please wait a moment.",
    "auth/popup-closed-by-user": "Google sign-in was cancelled.",
    "auth/network-request-failed": "Network problem. Check your connection."
  };
  return map[code] || e?.message?.replace("Firebase:", "").trim() || "Something went wrong. Please try again.";
}

async function ensureProfile(fbUser, fallbackName) {
  const ref = doc(state.db, "users", fbUser.uid);
  const snap = await getDoc(ref).catch(() => null);
  if (snap && snap.exists()) return;
  const profile = {
    id: fbUser.uid,
    fullName: fbUser.displayName || fallbackName || "New user",
    email: fbUser.email || "",
    role: "USER",
    bio: "",
    phoneNumber: "",
    location: "",
    createdAt: Date.now()
  };
  await setDoc(ref, profile).catch((e) => toast(friendlyAuthError(e), true));
}

function watchProfile(uid) {
  unsubscribeProfile?.();
  unsubscribeProfile = onSnapshot(doc(state.db, "users", uid), (snap) => {
    const next = snap.exists() ? { id: snap.id, ...snap.data() } : null;
    const roleChanged = (next?.role || null) !== (state.profile?.role || null);
    state.profile = next;
    if (roleChanged) restartTicketsStream();
    scheduleRender();
  }, (err) => {
    console.warn("profile listener unavailable:", err.code || err.message);
  });
}

function restartTicketsStream() {
  unsubscribeTickets?.();
  state.tickets = [];
  state.ticketsLoading = true;

  if (!state.db || !state.fbUser) return;

  let q;
  if (isAgent()) {
    q = query(collection(state.db, "tickets"), orderBy("createdAt", "desc"), fsLimit(100));
  } else {
    q = query(
      collection(state.db, "tickets"),
      where("creatorId", "==", state.fbUser.uid),
      orderBy("createdAt", "desc"),
      fsLimit(50)
    );
  }

  unsubscribeTickets = onSnapshot(q, (snap) => {
    state.tickets = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
    state.ticketsLoading = false;
    scheduleRender();
  }, (err) => {
    state.ticketsLoading = false;
    toast(err.message, true);
  });

  const since = Date.now() - CONVERSATION_WINDOW_MS;
  unsubscribeCommentsCG?.();
  if (window.__DISABLE_CG__) return;
  unsubscribeCommentsCG = onSnapshot(
    query(collectionGroup(state.db, "comments"), where("timestamp", ">", since), orderBy("timestamp", "desc"), fsLimit(200)),
    (snap) => {
      const mine = new Set(state.tickets.map((t) => t.id));
      const latest = new Map();
      for (const d of snap.docs) {
        const ticketId = d.ref.parent.parent?.id;
        if (!ticketId || !mine.has(ticketId)) continue;
        if (!latest.has(ticketId)) latest.set(ticketId, { id: d.id, ...d.data() });
      }
      state.latestComments = latest;
      scheduleRender();
    },
    (err) => {
      console.warn("comments feed unavailable:", err.code || err.message);
    }
  );
}

function stopStreams() {
  [unsubscribeTickets, unsubscribeCommentsCG, unsubscribeProfile].forEach(fn => fn?.());
  unsubscribeTickets = unsubscribeCommentsCG = unsubscribeProfile = null;
  state.tickets = [];
  state.latestComments = new Map();
  state.profile = null;
}

let renderQueued = false;
function scheduleRender() {
  if (renderQueued) return;
  renderQueued = true;
  requestAnimationFrame(() => {
    renderQueued = false;
    render();
  });
}

function parseHash() {
  const hash = location.hash.replace(/^#\/?/, "");
  const parts = hash.split("/").filter(Boolean);
  if (parts[0] === "tickets") return { name: "tickets" };
  if (parts[0] === "ticket" && parts[1]) return { name: "detail", id: parts[1] };
  if (parts[0] === "inbox") return { name: "inbox" };
  if (parts[0] === "profile") return { name: "profile" };
  return { name: "home" };
}

window.addEventListener("hashchange", () => {
  state.route = parseHash();
  state.chatText = "";
  state.attachment = null;
  if (state.route.name !== "detail") {
    stopDetailComments();
  } else if (state.detail?.id !== state.route.id) {
    openDetail(state.route.id);
  }
  if (state.route.name === "inbox") localStorage.setItem(lastSeenKey(), String(Date.now()));
  scheduleRender();
});

function nav(route) {
  location.hash = route;
}

function conversations() {
  const uid = state.fbUser?.uid;
  const seen = lastSeen();
  const byId = new Map(state.tickets.map(t => [t.id, t]));
  const convs = [];
  for (const [ticketId, comment] of state.latestComments) {
    const ticket = byId.get(ticketId);
    if (!ticket) continue;
    const snippet = comment.content?.trim()
      ? comment.content.trim()
      : (comment.imageUrl ? "Photo" : null);
    if (!snippet) continue;
    convs.push({
      ticketId,
      ticketNumber: ticket.ticketNumber,
      subject: ticket.subject,
      authorName: comment.authorName || "Someone",
      snippet,
      timestamp: comment.timestamp,
      fromMe: comment.authorId != null && comment.authorId === uid,
      unread: comment.authorId !== uid && comment.timestamp > seen
    });
  }
  return convs.sort((a, b) => b.timestamp - a.timestamp);
}

function unreadCount() {
  return conversations().filter(c => c.unread).length;
}

function notificationsFeed() {
  const uid = state.fbUser?.uid;
  const name = (state.profile?.fullName || "").toLowerCase();
  const prefs = notifPrefs();
  const items = [];
  const now = Date.now();

  for (const t of state.tickets) {
    if (now - t.createdAt < ONE_DAY_MS) {
      items.push({
        id: t.id + "-received",
        glyph: "in",
        title: "We received your ticket",
        message: "#" + t.ticketNumber + " · " + t.subject,
        timestamp: t.createdAt,
        ticketId: t.id
      });
    }
    if (prefs.status && t.status && t.status !== "OPEN") {
      items.push({
        id: `${t.id}-status-${t.status}-${t.updatedAt}`,
        glyph: "status",
        title: statusChangeTitle(t.status),
        message: "#" + t.ticketNumber + " · " + t.subject,
        timestamp: Math.max(t.updatedAt || 0, t.createdAt || 0),
        ticketId: t.id
      });
    }
    const c = state.latestComments.get(t.id);
    if (prefs.comments && c && now - c.timestamp < COMMENT_FRESHNESS_MS &&
        !(name && (c.authorName || "").toLowerCase() === name) &&
        !(c.authorId && c.authorId === uid)) {
      items.push({
        id: `${t.id}-comment-${c.id}`,
        glyph: "chat",
        title: (c.authorName || "Someone") + " responded",
        message: (c.content || "Photo").slice(0, 120),
        timestamp: c.timestamp,
        ticketId: t.id
      });
    }
    if (prefs.priority && (t.priority === "HIGH" || t.priority === "CRITICAL") &&
        t.status !== "RESOLVED" && t.status !== "CLOSED") {
      items.push({
        id: t.id + "-priority",
        glyph: "prio",
          title: statusLabel(t.priority) + " priority ticket needs attention",
        message: "#" + t.ticketNumber + " · " + t.subject,
        timestamp: Math.max(t.updatedAt || 0, t.createdAt || 0),
        ticketId: t.id
      });
    }
  }

  const seenIds = new Set();
  return items
    .filter(i => !seenIds.has(i.id) && seenIds.add(i.id))
    .sort((a, b) => b.timestamp - a.timestamp)
    .slice(0, 30);
}

function statusChangeTitle(status) {
  switch (status) {
    case "RESOLVED": return "Your ticket was resolved";
    case "CLOSED": return "Your ticket was closed";
    case "IN_PROGRESS": return "Work started on your ticket";
    case "ASSIGNED": return "An agent picked up your ticket";
    case "WAITING_FOR_USER": return "We need more information from you";
    case "REOPENED": return "Your ticket was reopened";
    default: return "Ticket updated";
  }
}

function visibleTickets() {
  let list = state.tickets;
  const term = state.ticketSearch.trim().toLowerCase();
  if (term) {
    list = list.filter(t =>
      (t.subject || "").toLowerCase().includes(term) ||
      (t.ticketNumber || "").toLowerCase().includes(term) ||
      (t.description || "").toLowerCase().includes(term));
  }
  if (state.ticketFilter !== "ALL") {
    if (state.ticketFilter === "ACTIVE") {
      list = list.filter(t => !["RESOLVED", "CLOSED"].includes(t.status));
    } else if (state.ticketFilter === "DONE") {
      list = list.filter(t => ["RESOLVED", "CLOSED"].includes(t.status));
    } else {
      list = list.filter(t => t.status === state.ticketFilter);
    }
  }
  return list;
}

function ticketCard(t) {
  return `
    <a class="ticket-card" href="#/ticket/${esc(t.id)}">
      <div class="top">
        <span class="num">#${esc(t.ticketNumber)}</span>
        <span class="chip ${chipClass(t.status)}">${esc(statusLabel(t.status))}</span>
      </div>
      <h4>${esc(t.subject)}</h4>
      <span class="prio ${esc(t.priority)}">${esc(statusLabel(t.priority))}</span>
      <div class="meta">
        <span>${esc(t.category || "General")}</span>
        <span>&middot;</span>
        <span>${timeAgo(t.createdAt)}</span>
        ${t.assignedAgentName ? `<span>&middot;</span><span>${esc(t.assignedAgentName)}</span>` : ""}
      </div>
    </a>`;
}

function skeletonList(n = 4) {
  return Array.from({ length: n }, () => '<div class="skel"></div>').join("");
}

function emptyState(icon, title, text) {
  return `
    <div class="empty">
      <div class="big">${icon}</div>
      <h3>${esc(title)}</h3>
      <p>${esc(text)}</p>
    </div>`;
}

function renderAuth(root) {
  const login = state.authMode === "login";
  root.innerHTML = `
    <div class="auth-wrap">
      <div class="auth-card">
        <div class="auth-logo">
          <img src="logo.png" alt="NextHelp">
          <h1>NextHelp</h1>
          <p>${login ? "Sign in to track your support tickets" : "Create an account to get started"}</p>
        </div>
        ${state.authError ? `<div class="form-error">${esc(state.authError)}</div>` : ""}
        <form id="auth-form">
          ${login ? "" : `
          <div class="field">
            <label for="f-name">Full name</label>
            <input id="f-name" type="text" autocomplete="name" placeholder="Alex Doe" required>
          </div>`}
          <div class="field">
            <label for="f-email">Email</label>
            <input id="f-email" type="email" autocomplete="email" placeholder="you@example.com" required>
          </div>
          <div class="field">
            <label for="f-pass">Password</label>
            <input id="f-pass" type="password" autocomplete="${login ? "current-password" : "new-password"}" placeholder="&bull;&bull;&bull;&bull;&bull;&bull;&bull;&bull;" required minlength="6">
          </div>
          <button class="btn block" type="submit" ${state.authBusy ? "disabled" : ""}>
            ${login ? "Sign in" : "Create account"}
          </button>
        </form>
        <div style="height:12px"></div>
        <button class="btn ghost block" id="google-btn" ${state.authBusy ? "disabled" : ""}>
          <svg width="18" height="18" viewBox="0 0 24 24"><path fill="#EA4335" d="M12 5.29c1.95 0 3.7.67 5.07 1.98l3.03-3.03C17.46 1.44 15.03.5 12 .5 7.6.5 3.8 3.02 1.96 6.75l3.54 2.75C6.35 7.02 8.94 5.29 12 5.29z"/><path fill="#4285F4" d="M23.49 12.27c0-.85-.08-1.66-.22-2.45H12v4.64h6.47c-.28 1.5-1.13 2.77-2.4 3.62l3.68 2.86c2.15-1.99 3.74-4.93 3.74-8.67z"/><path fill="#FBBC05" d="M5.5 14.5c-.25-.73-.38-1.51-.38-2.32s.14-1.59.38-2.32L1.96 7.11C1.16 8.63.5 10.53.5 12.18s.66 3.55 1.46 5.07l3.54-2.75z"/><path fill="#34A853" d="M12 23.5c3.04 0 5.6-1 7.46-2.72l-3.68-2.86c-1.02.69-2.33 1.1-3.78 1.1-3.06 0-5.65-1.73-6.5-4.32l-3.54 2.75C3.8 21.16 7.6 23.5 12 23.5z"/></svg>
          Continue with Google
        </button>
        <div class="auth-alt">
          ${login
            ? `<a href="#" id="forgot-link">Forgot password?</a>`
            : ""}
        </div>
        <div class="auth-alt">
          ${login ? `New here? <a href="#" id="switch-mode">Create an account</a>` : `Already registered? <a href="#" id="switch-mode">Sign in</a>`}
        </div>
      </div>
    </div>`;

  $("#auth-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const email = $("#f-email").value.trim();
    const password = $("#f-pass").value;
    state.authBusy = true;
    state.authError = "";
    scheduleRender();
    try {
      if (login) {
        await signInWithEmailAndPassword(state.auth, email, password);
      } else {
        const name = $("#f-name").value.trim();
        const cred = await createUserWithEmailAndPassword(state.auth, email, password);
        if (name) await updateProfile(cred.user, { displayName: name });
        await ensureProfile(cred.user, name);
      }
    } catch (err) {
      state.authError = friendlyAuthError(err);
    } finally {
      state.authBusy = false;
      scheduleRender();
    }
  });

  $("#google-btn").addEventListener("click", async () => {
    state.authBusy = true;
    state.authError = "";
    scheduleRender();
    try {
      const provider = new GoogleAuthProvider();
      const cred = await signInWithPopup(state.auth, provider);
      await ensureProfile(cred.user, cred.user.displayName);
    } catch (err) {
      state.authError = friendlyAuthError(err);
    } finally {
      state.authBusy = false;
      scheduleRender();
    }
  });

  $("#forgot-link")?.addEventListener("click", async (e) => {
    e.preventDefault();
    const email = $("#f-email").value.trim();
    if (!email) {
      state.authError = "Enter your email above first, then tap Forgot password.";
      scheduleRender();
      return;
    }
    try {
      await sendPasswordResetEmail(state.auth, email);
      toast("Password reset email sent to " + email);
    } catch (err) {
      state.authError = friendlyAuthError(err);
      scheduleRender();
    }
  });

  $("#switch-mode").addEventListener("click", (e) => {
    e.preventDefault();
    state.authMode = login ? "register" : "login";
    state.authError = "";
    scheduleRender();
  });
}

function navLinks(active) {
  const unread = unreadCount();
  const links = [
    { key: "home", href: "#/", label: "Home", icon: ICONS.home },
    { key: "tickets", href: "#/tickets", label: isAgent() ? "All tickets" : "My tickets", icon: ICONS.ticket },
    { key: "inbox", href: "#/inbox", label: "Inbox", icon: ICONS.bell, badge: unread },
    { key: "profile", href: "#/profile", label: "Profile", icon: ICONS.user }
  ];
  const linkHtml = (l) => `
    <a href="${l.href}" class="${active === l.key ? "active" : ""}">
      ${l.icon}<span>${esc(l.label)}</span>
      ${l.badge ? `<span class="badge">${l.badge > 9 ? "9+" : l.badge}</span>` : ""}
    </a>`;
  return `
    <aside class="sidebar">
      <div class="brand"><img src="logo.png" alt=""><strong>NextHelp</strong></div>
      <button class="side-cta" id="side-new">${ICONS.plus}<span>New ticket</span></button>
      ${links.map(linkHtml).join("")}
      <div class="side-footer">
        <a href="#" class="side-link" id="side-signout">${ICONS.logout}<span>Sign out</span></a>
      </div>
    </aside>`;
}

function bottomNav(active) {
  const unread = unreadCount();
  const item = (key, href, icon, label, badge) => `
    <a href="${href}" class="${active === key ? "active" : ""}">
      ${icon}${badge ? `<span class="badge">${badge > 9 ? "9+" : badge}</span>` : ""}<span>${esc(label)}</span>
    </a>`;
  return `
    <nav class="bottombar" aria-label="Primary">
      ${item("home", "#/", ICONS.home, "Home")}
      ${item("tickets", "#/tickets", ICONS.ticket, "Tickets")}
      <span class="create-item">
        <button class="create-btn" id="nav-create" aria-label="Create new ticket">${ICONS.plus}</button>
      </span>
      ${item("inbox", "#/inbox", ICONS.bell, "Inbox", unread)}
      ${item("profile", "#/profile", ICONS.user, "Profile")}
    </nav>`;
}

function bindShell(root) {
  $("#nav-create", root)?.addEventListener("click", () => openNewTicketDialog());
  $("#side-new", root)?.addEventListener("click", () => openNewTicketDialog());
  $("#side-signout", root)?.addEventListener("click", async (e) => {
    e.preventDefault();
    await doSignOut();
  });
}

async function doSignOut() {
  try {
    await signOut(state.auth);
    stopStreams();
    stopDetailComments();
    state.route = { name: "home" };
    state.authMode = "login";
    state.authError = "";
    location.hash = "";
    scheduleRender();
  } catch (err) {
    toast(err.message, true);
  }
}

function statBox(icon, bg, color, value, label) {
  return `
    <div class="stat">
      <div class="icon" style="background:${bg};color:${color}">${icon}</div>
      <div><div class="value">${value}</div><div class="label">${esc(label)}</div></div>
    </div>`;
}

function renderHome(root) {
  const tickets = state.tickets;
  const open = tickets.filter(t => ["OPEN", "REOPENED"].includes(t.status)).length;
  const active = tickets.filter(t => ["ASSIGNED", "IN_PROGRESS", "WAITING_FOR_USER"].includes(t.status)).length;
  const done = tickets.filter(t => ["RESOLVED", "CLOSED"].includes(t.status)).length;
  const feed = notificationsFeed().slice(0, 3);

  const hour = new Date().getHours();
  const greeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";

  const recent = tickets.slice(0, 5);

  root.innerHTML = `
    <div class="shell">
    ${navLinks("home")}
    <main class="main">
      <div class="content">
        <div class="page-head">
          <div class="home-head">
            <a href="#/profile" aria-label="Open profile">${avatarHtml(state.profile?.profileImageUrl, state.profile?.fullName)}</a>
            <div>
              <div class="page-title">${greeting}, ${esc((state.profile?.fullName || "").split(" ")[0] || "there")}</div>
              <div class="page-sub">Here's what's happening with your support requests.</div>
            </div>
          </div>
        </div>
        <div class="section-title">Overview</div>
        <div class="stat-grid">
          ${statBox(ICONS.inbox, "var(--accent-soft)", "var(--accent)", open, "Open")}
          ${statBox(ICONS.clock, "rgba(245,184,61,.15)", "var(--amber)", active, "In progress")}
          ${statBox(ICONS.check, "rgba(63,206,119,.15)", "var(--green)", done, "Resolved")}
          ${statBox(ICONS.ticket, "var(--card-soft)", "var(--purple)", tickets.length, "Total")}
        </div>
        <div class="section-title">Quick actions</div>
        <div class="quick-grid">
          <button class="quick" id="qa-new"><span class="qi" style="color:var(--accent)">${ICONS.plus}</span><strong>New ticket</strong><span class="muted" style="font-size:12.5px">Report an issue</span></button>
          <button class="quick" onclick="location.hash='#/tickets'"><span class="qi" style="color:var(--purple)">${ICONS.ticket}</span><strong>Browse tickets</strong><span class="muted" style="font-size:12.5px">${isAgent() ? "All submitted tickets" : "Your submissions"}</span></button>
          <button class="quick" onclick="location.hash='#/inbox'"><span class="qi" style="color:var(--amber)">${ICONS.bell}</span><strong>Inbox</strong><span class="muted" style="font-size:12.5px">Replies and updates</span></button>
          <a class="quick" href="download.html"><span class="qi" style="color:var(--green)">${ICONS.send}</span><strong>Get the app</strong><span class="muted" style="font-size:12.5px">Download Android APK</span></a>
        </div>
        ${feed.length ? `
        <div class="section-title">Latest updates</div>
        <div class="ticket-list">
          ${feed.map((n) => `
            <a class="preview-note" href="#/ticket/${esc(n.ticketId)}">
              <span class="glyph">${n.glyph === "chat" ? ICONS.send : n.glyph === "prio" ? ICONS.alert : ICONS.clock}</span>
              <div class="body"><strong>${esc(n.title)}</strong><span>${esc(n.message)}</span></div>
              <span class="time">${timeAgo(n.timestamp)}</span>
            </a>`).join("")}
          <a href="#/inbox" class="muted" style="font-size:12.5px;font-weight:700;padding:4px 2px">View all in Inbox &rarr;</a>
        </div>` : ""}
        <div class="section-title">Recent tickets</div>
        ${state.ticketsLoading && !tickets.length
          ? skeletonList()
          : recent.length
            ? `<div class="ticket-list">${recent.map(ticketCard).join("")}</div>
               ${tickets.length > 5 ? `<div class="spacer"></div><a href="#/tickets" class="btn ghost small" style="width:100%">View all ${tickets.length} tickets</a>` : ""}`
            : emptyState("🎫", "No tickets yet", "Create your first support ticket and we'll get right on it.")}
        <div class="footer-note">NextHelp · <a href="download.html">Android app</a></div>
      </div>
    </main>
    </div>
    ${bottomNav("home")}`;
  bindShell(root);
  $("#qa-new", root).addEventListener("click", () => openNewTicketDialog());
}

function filterChips() {
  const chips = [
    ["ALL", "All"],
    ["ACTIVE", "Active"],
    ["OPEN", "Open"],
    ["IN_PROGRESS", "In progress"],
    ["WAITING_FOR_USER", "Waiting"],
    ["RESOLVED", "Resolved"],
    ["CLOSED", "Closed"]
  ];
  return chips
    .map(([key, label]) => `<button class="fchip ${state.ticketFilter === key ? "on" : ""}" data-filter="${key}">${label}</button>`)
    .join("");
}

function renderTickets(root) {
  const list = visibleTickets();
  root.innerHTML = `
    <div class="shell">
    ${navLinks("tickets")}
    <main class="main">
      <div class="content">
        <div class="page-head">
          <div>
            <div class="page-title">${isAgent() ? "All tickets" : "My tickets"}</div>
            <div class="page-sub">${list.length} ticket${list.length === 1 ? "" : "s"}${state.ticketSearch ? " matching \"" + esc(state.ticketSearch) + "\"" : ""}</div>
          </div>
        </div>
        <input class="search" id="ticket-search" type="search" placeholder="Search subject, description or number…" value="${esc(state.ticketSearch)}">
        <div class="filter-row" id="filter-row">${filterChips()}</div>
        <div id="ticket-list">
          ${state.ticketsLoading && !state.tickets.length
            ? skeletonList()
            : list.length
              ? `<div class="ticket-list">${list.map(ticketCard).join("")}</div>`
              : emptyState("🗂️", "Nothing here", state.ticketSearch || state.ticketFilter !== "ALL"
                  ? "No tickets match the current search or filters."
                  : "Tickets you submit will show up here.")}
        </div>
      </div>
    </main>
    </div>
    ${bottomNav("tickets")}`;
  bindShell(root);

  const searchEl = $("#ticket-search", root);
  let debounce;
  searchEl.addEventListener("input", () => {
    clearTimeout(debounce);
    debounce = setTimeout(() => {
      state.ticketSearch = searchEl.value;
      const focusId = document.activeElement?.id;
      const pos = searchEl.selectionStart;
      $("#ticket-list", root).innerHTML = renderTicketListInner();
      $(".page-sub", root).textContent = `${visibleTickets().length} ticket${visibleTickets().length === 1 ? "" : "s"}${state.ticketSearch ? " matching \"" + state.ticketSearch + "\"" : ""}`;
      if (focusId) {
        const el = document.getElementById(focusId);
        el?.focus();
        try { el.setSelectionRange(pos, pos); } catch (_) {}
      }
    }, 120);
  });

  $("#filter-row", root).addEventListener("click", (e) => {
    const btn = e.target.closest("[data-filter]");
    if (!btn) return;
    state.ticketFilter = btn.dataset.filter;
    $$("#filter-row .fchip", root).forEach(c => c.classList.toggle("on", c.dataset.filter === state.ticketFilter));
    $("#ticket-list", root).innerHTML = renderTicketListInner();
  });
}

function renderTicketListInner() {
  const list = visibleTickets();
  if (state.ticketsLoading && !state.tickets.length) return skeletonList();
  if (!list.length) {
    return emptyState("🗂️", "Nothing here", "No tickets match the current filters.");
  }
  return `<div class="ticket-list">${list.map(ticketCard).join("")}</div>`;
}

function renderInbox(root) {
  const convs = conversations();
  const feed = notificationsFeed();

  const groups = new Map();
  for (const n of feed) {
    const label = dayGroupLabel(n.timestamp);
    if (!groups.has(label)) groups.set(label, []);
    groups.get(label).push(n);
  }

  const glyphIcon = (g) => g === "chat" ? ICONS.send : g === "status" ? ICONS.clock : g === "prio" ? ICONS.alert : ICONS.inbox;

  root.innerHTML = `
    <div class="shell">
    ${navLinks("inbox")}
    <main class="main">
      <div class="content wide">
        <div class="page-head">
          <div>
            <div class="page-title">Inbox</div>
            <div class="page-sub">Latest activity across your tickets</div>
          </div>
        </div>
        <div class="section-title">Chats</div>
        ${convs.length
          ? `<div class="ticket-list">${convs.map((c) => `
              <a class="conv ${c.unread ? "unread" : ""}" href="#/ticket/${esc(c.ticketId)}">
                <span class="avatar">${esc((c.fromMe ? (state.profile?.fullName || "Me") : c.authorName).charAt(0).toUpperCase())}</span>
                <div class="body">
                  <div class="row1">
                    <strong>${esc(c.fromMe ? "You" : c.authorName)}</strong>
                    ${c.unread ? '<span class="dot"></span>' : ""}
                    <span class="time">${timeAgo(c.timestamp)}</span>
                  </div>
                  <div class="snippet">${esc(c.snippet)}</div>
                  <div class="thread">#${esc(c.ticketNumber)} · ${esc(c.subject)}</div>
                </div>
              </a>`).join("")}</div>`
          : emptyState("💬", "No messages yet", "When you or support reply on a ticket, threads appear here.")}
        <div class="section-title">Activity</div>
        ${feed.length
          ? [...groups.entries()].map(([label, items]) => `
              <div class="muted" style="font-size:12px;font-weight:700;margin:14px 2px 8px">${esc(label)}</div>
              <div class="ticket-list">
                ${items.map((n) => `
                  <a class="notif" href="#/ticket/${esc(n.ticketId)}">
                    <span class="glyph">${glyphIcon(n.glyph)}</span>
                    <div class="body">
                      <strong>${esc(n.title)}</strong>
                      <p>${esc(n.message)}</p>
                      <div class="time">${fmtDateTime(n.timestamp)}</div>
                    </div>
                  </a>`).join("")}
              </div>`).join("")
          : emptyState("🔔", "All caught up", "Status changes and replies will be listed here.")}
      </div>
    </main>
    </div>
    ${bottomNav("inbox")}`;
  bindShell(root);
}

function stopDetailComments() {
  unsubscribeDetailComments?.();
  unsubscribeDetailComments = null;
  if (state.detail) state.detail = { ...state.detail, comments: [], commentsLoading: true };
}

function openDetail(id) {
  stopDetailComments();
  const ticket = state.tickets.find(t => t.id === id) || null;
  state.detail = { id, ticket, comments: [], commentsLoading: true };
  if (state.db && id) {
    unsubscribeDetailComments = onSnapshot(
      collection(state.db, "tickets", id, "comments"),
      (snap) => {
        if (state.detail?.id !== id) return;
        state.detail.comments = snap.docs
          .map(d => ({ id: d.id, ...d.data() }))
          .sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0));
        state.detail.commentsLoading = false;
        scheduleRender();
      },
      (err) => {
        console.warn("comments listener unavailable:", err.code || err.message);
        if (state.detail?.id !== id) return;
        state.detail.commentsLoading = false;
        scheduleRender();
      }
    );
  }
}

function detailActions(t) {
  const terminal = ["RESOLVED", "CLOSED"].includes(t.status);
  const buttons = [];
  if (isAgent()) {
    const options = STATUSES.filter(s => s !== t.status)
      .map(s => `<option value="${s}">${statusLabel(s)}</option>`).join("");
    buttons.push(`
      <select id="status-select" class="fchip" style="padding:9px 12px;border-radius:12px">
        <option value="" selected disabled>Change status…</option>
        ${options}
      </select>`);
  } else if (terminal) {
    buttons.push(`<button class="btn ghost small" id="reopen-btn">Reopen ticket</button>`);
  }
  return buttons.join("");
}

async function applyStatus(newStatus) {
  const t = state.detail?.ticket;
  if (!t || !newStatus || state.statusBusy) return;
  state.statusBusy = true;
  const patch = {
    status: newStatus,
    updatedAt: Date.now(),
    updatedBy: state.fbUser.uid
  };
  if (newStatus === "RESOLVED") {
    patch.resolvedAt = Date.now();
    patch.closedAt = deleteField();
  } else if (newStatus === "CLOSED") {
    patch.closedAt = Date.now();
  } else {
    patch.resolvedAt = deleteField();
    patch.closedAt = deleteField();
  }
  try {
    await updateDoc(doc(state.db, "tickets", t.id), patch);
    const localTicket = { ...t, ...patch };
    if (patch.resolvedAt === deleteField()) localTicket.resolvedAt = null;
    if (patch.closedAt === deleteField()) localTicket.closedAt = null;
    state.detail = { ...state.detail, ticket: localTicket };
    toast("Status updated to " + statusLabel(newStatus));
  } catch (err) {
    toast(err.message, true);
  } finally {
    state.statusBusy = false;
    scheduleRender();
  }
}

function statusTimeline(t) {
  const stages = [
    { key: "created", title: "Ticket created", sub: fmtDateTime(t.createdAt) },
    { key: "assigned", title: "Assigned", sub: t.assignedAgentName ? "Handled by " + t.assignedAgentName : "Waiting for an agent" },
    { key: "progress", title: "In progress", sub: "Work underway" },
    { key: "resolved", title: "Resolved", sub: t.resolvedAt ? fmtDateTime(t.resolvedAt) : "Not yet resolved" }
  ];
  const s = t.status;
  let reached = 0;
  if (["ASSIGNED", "IN_PROGRESS", "WAITING_FOR_USER", "RESOLVED", "CLOSED", "REOPENED"].includes(s)) reached = Math.max(reached, 1);
  if (["IN_PROGRESS", "WAITING_FOR_USER", "RESOLVED", "CLOSED"].includes(s)) reached = Math.max(reached, 2);
  if (["RESOLVED", "CLOSED"].includes(s)) reached = 3;
  if (s === "REOPENED") reached = 2;

  const activeIdx = s === "OPEN" || s === "REOPENED" ? 0
    : s === "ASSIGNED" ? 1
    : s === "IN_PROGRESS" || s === "WAITING_FOR_USER" ? 2
    : 3;

  return `
    <div class="card" style="margin-top:14px">
      <div class="section-title" style="margin-top:0">Progress</div>
      <div class="timeline">
        ${stages.map((st, i) => {
          const done = i < reached || (i === reached && i === 3 && ["RESOLVED", "CLOSED"].includes(s));
          const active = i === activeIdx && !done && !(i === 3 && !["RESOLVED", "CLOSED"].includes(s)) || (i === 3 && ["RESOLVED", "CLOSED"].includes(s) && reached === 3);
          const cls = done ? "done" : (i === activeIdx ? "active" : "");
          return `
            <div class="tl-step ${cls}">
              <div class="rail"><span class="dotc"></span>${i < stages.length - 1 ? '<span class="line"></span>' : ""}</div>
              <div class="tl-body">
                <div class="tl-title">${esc(st.title)}${active && !done ? " · current" : ""}</div>
                <div class="tl-sub">${esc(st.sub)}</div>
              </div>
            </div>`;
        }).join("")}
      </div>
    </div>`;
}

function renderDetail(root) {
  const d = state.detail;
  if (!d) {
    openDetail(state.route.id);
    root.innerHTML = `
      <div class="shell">
      ${navLinks("tickets")}
      <main class="main"><div class="content">${skeletonList(3)}</div></main>
      </div>
      ${bottomNav("tickets")}`;
    bindShell(root);
    return;
  }

  const t = d.ticket ?? state.tickets.find(x => x.id === d.id) ?? null;

  if (!t && state.ticketsLoading) {
    root.innerHTML = `
      <div class="shell">
      ${navLinks("tickets")}
      <main class="main"><div class="content">${skeletonList(3)}</div></main>
      </div>
      ${bottomNav("tickets")}`;
    bindShell(root);
    return;
  }

  if (!t) {
    root.innerHTML = `
      <div class="shell">
      ${navLinks("tickets")}
      <main class="main"><div class="content">
        <a class="back-link" href="#/tickets">${ICONS.back} Back to tickets</a>
        ${emptyState("🔍", "Ticket not found", "It may belong to another account or was deleted.")}
      </div></main>
      </div>
      ${bottomNav("tickets")}`;
    bindShell(root);
    return;
  }

  const comments = d.comments || [];
  const uid = state.fbUser.uid;

  const bubbles = d.commentsLoading && !comments.length
    ? '<div class="skel" style="height:56px"></div><div class="skel" style="height:56px;width:70%"></div>'
    : comments.length
      ? comments.map((c) => {
          const mine = c.authorId === uid;
          return `
            <div class="bubble ${mine ? "mine" : ""}">
              ${!mine ? `<div class="who">${esc(c.authorName || "Someone")}</div>` : ""}
              ${c.content ? esc(c.content).replace(/\n/g, "<br>") : ""}
              ${c.imageUrl ? `<img class="att" src="${esc(c.imageUrl)}" alt="attachment">` : ""}
              <div class="when">${timeAgo(c.timestamp)}</div>
            </div>`;
        }).join("")
      : emptyState("", "No responses yet", "Start the conversation below.");

  root.innerHTML = `
    <div class="shell">
    ${navLinks("tickets")}
    <main class="main">
      <div class="content wide">
        <a class="back-link" href="#/tickets">${ICONS.back} Back to tickets</a>
        <div class="detail-head">
          <div class="top" style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
            <span class="chip ${chipClass(t.status)}">${esc(statusLabel(t.status))}</span>
            <span class="prio ${esc(t.priority)}">${esc(statusLabel(t.priority))} priority</span>
          </div>
          <div class="card detail-card">
            <span class="num muted" style="font-size:11.5px;font-weight:700;letter-spacing:.04em">#${esc(t.ticketNumber)}</span>
            <h2>${esc(t.subject)}</h2>
            <div class="desc">${esc(t.description)}</div>
            <dl class="kv">
              <dt>Category</dt><dd>${esc(t.category || "—")}</dd>
              <dt>Requester</dt><dd>${esc(t.requesterName || "—")}</dd>
              ${t.requesterPhone ? `<dt>Phone</dt><dd>${esc(t.requesterPhone)}</dd>` : ""}
              ${t.requesterLocation ? `<dt>Location</dt><dd>${esc(t.requesterLocation)}</dd>` : ""}
              <dt>Created</dt><dd>${fmtDateTime(t.createdAt)}</dd>
              <dt>Last update</dt><dd>${t.updatedAt ? fmtDateTime(t.updatedAt) : "—"}</dd>
              ${t.assignedAgentName ? `<dt>Assignee</dt><dd>${esc(t.assignedAgentName)}</dd>` : ""}
            </dl>
            ${(isAgent() || ["RESOLVED", "CLOSED"].includes(t.status))
              ? `<div class="actions-row" id="detail-actions">${detailActions(t)}</div>`
              : ""}
          </div>
        </div>
        ${statusTimeline(t)}
        <div class="card chat-panel">
          <div class="chat-scroll" id="chat-scroll">${bubbles}</div>
          ${state.attachment ? `
            <div class="att-preview">
              <img src="${esc(state.attachment)}" alt="preview">
              <button id="remove-att" aria-label="Remove attachment">&times;</button>
            </div>` : ""}
          <div class="chat-bar">
            <button class="icon-btn" id="attach-btn" title="Attach photo">${ICONS.clip}</button>
            <textarea id="chat-input" rows="1" placeholder="Write a reply…">${esc(state.chatText)}</textarea>
            <button class="send-btn" id="send-btn" title="Send" ${state.sending ? "disabled" : ""}>${ICONS.send}</button>
          </div>
          <input type="file" id="file-input" accept="image/*" hidden>
        </div>
        <div class="footer-note">Ticket #${esc(t.ticketNumber)}</div>
      </div>
    </main>
    </div>
    ${bottomNav("tickets")}`;
  bindShell(root);

  const scroll = $("#chat-scroll", root);
  scroll.scrollTop = scroll.scrollHeight;

  $("#status-select", root)?.addEventListener("change", (e) => applyStatus(e.target.value));
  $("#reopen-btn", root)?.addEventListener("click", () => applyStatus("REOPENED"));

  const input = $("#chat-input", root);
  input.addEventListener("input", () => {
    state.chatText = input.value;
    input.style.height = "auto";
    input.style.height = Math.min(input.scrollHeight, 120) + "px";
  });
  input.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendComment();
    }
  });

  $("#attach-btn", root).addEventListener("click", () => $("#file-input", root).click());
  $("#file-input", root).addEventListener("change", async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      state.attachment = await compressImage(file, 1280, 0.75);
      scheduleRender();
    } catch (_) {
      toast("Couldn't read that image. Try another one.", true);
    }
  });
  $("#remove-att", root)?.addEventListener("click", () => {
    state.attachment = null;
    scheduleRender();
  });

  $("#send-btn", root).addEventListener("click", sendComment);
}

async function sendComment() {
  const d = state.detail;
  if (!d || state.sending) return;
  const content = state.chatText.trim();
  if (!content && !state.attachment) return;
  state.sending = true;
  scheduleRender();
  const payload = {
    authorId: state.fbUser.uid,
    authorName: state.profile?.fullName || state.fbUser.displayName || "User",
    content,
    timestamp: Date.now()
  };
  if (state.attachment) payload.imageUrl = state.attachment;
  try {
    await addDoc(collection(state.db, "tickets", d.id, "comments"), payload);
    await updateDoc(doc(state.db, "tickets", d.id), { updatedAt: Date.now(), updatedBy: state.fbUser.uid }).catch(() => {});
    state.chatText = "";
    state.attachment = null;
  } catch (err) {
    toast(err.message, true);
  } finally {
    state.sending = false;
    scheduleRender();
  }
}

function dialogShell(inner) {
  const overlay = document.createElement("div");
  overlay.className = "overlay";
  overlay.innerHTML = `<div class="dialog">${inner}</div>`;
  overlay.addEventListener("click", (e) => { if (e.target === overlay) overlay.remove(); });
  document.body.appendChild(overlay);
  return overlay;
}

function openNewTicketDialog() {
  const p = state.profile || {};
  const overlay = dialogShell(`
    <h3>New support ticket</h3>
    <form id="nt-form">
      <div class="field">
        <label for="nt-subject">Subject</label>
        <input id="nt-subject" type="text" placeholder="Brief summary of the issue" required maxlength="120">
      </div>
      <div class="field">
        <label for="nt-desc">Description</label>
        <textarea id="nt-desc" placeholder="What happened? What did you expect?" required></textarea>
      </div>
      <div class="field field-row">
        <div>
          <label for="nt-cat">Category</label>
          <select id="nt-cat">${CATEGORIES.map(c => `<option>${c}</option>`).join("")}</select>
        </div>
        <div>
          <label for="nt-prio">Priority</label>
          <select id="nt-prio">${PRIORITIES.map(p => `<option value="${p}" ${p === "MEDIUM" ? "selected" : ""}>${statusLabel(p)}</option>`).join("")}</select>
        </div>
      </div>
      <div class="field">
        <label for="nt-name">Your name</label>
        <input id="nt-name" type="text" value="${esc(p.fullName || state.fbUser.displayName || "")}" placeholder="Your name">
      </div>
      <div class="field field-row">
        <div>
          <label for="nt-phone">Phone (optional)</label>
          <input id="nt-phone" type="tel" value="${esc(p.phoneNumber || "")}">
        </div>
        <div>
          <label for="nt-loc">Location (optional)</label>
          <input id="nt-loc" type="text" value="${esc(p.location || "")}">
        </div>
      </div>
      <div class="field">
        <label>Photo (optional)</label>
        <div id="nt-att-slot">
          <button type="button" class="btn ghost small" id="nt-attach">${ICONS.clip} Attach a photo</button>
        </div>
        <input type="file" id="nt-file" accept="image/*" hidden>
      </div>
      <div class="row-btns">
        <button type="button" class="btn ghost" id="nt-cancel">Cancel</button>
        <button type="submit" class="btn" id="nt-submit">Submit ticket</button>
      </div>
    </form>`);

  let attachment = null;
  const renderAttSlot = () => {
    $("#nt-att-slot", overlay).innerHTML = attachment
      ? `<div class="att-preview" style="margin-bottom:0">
           <img src="${esc(attachment)}" alt="attachment preview">
           <button type="button" id="nt-att-remove" aria-label="Remove photo">&times;</button>
         </div>`
      : `<button type="button" class="btn ghost small" id="nt-attach">${ICONS.clip} Attach a photo</button>`;
    $("#nt-attach", overlay)?.addEventListener("click", () => $("#nt-file", overlay).click());
    $("#nt-att-remove", overlay)?.addEventListener("click", () => { attachment = null; renderAttSlot(); });
  };
  renderAttSlot();
  $("#nt-file", overlay).addEventListener("change", async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      attachment = await compressImage(file, 1280, 0.75);
      renderAttSlot();
    } catch (_) {
      toast("Couldn't read that image. Try another one.", true);
    }
  });
  $("#nt-cancel", overlay).addEventListener("click", () => overlay.remove());
  $("#nt-form", overlay).addEventListener("submit", async (e) => {
    e.preventDefault();
    const subject = $("#nt-subject", overlay).value.trim();
    const description = $("#nt-desc", overlay).value.trim();
    if (!subject || !description) return;
    const btn = $("#nt-submit", overlay);
    btn.disabled = true;
    btn.textContent = "Submitting…";
    const id = crypto.randomUUID();
    const now = Date.now();
    const requesterName = $("#nt-name", overlay).value.trim() ||
      state.profile?.fullName || state.fbUser.displayName || "User";
    const ticket = {
      id,
      ticketNumber: "NH-" + crypto.randomUUID().replace(/-/g, "").slice(0, 8).toUpperCase(),
      creatorId: state.fbUser.uid,
      requesterName,
      requesterPhone: $("#nt-phone", overlay).value.trim(),
      requesterEmail: state.profile?.email || state.fbUser.email || "",
      requesterLocation: $("#nt-loc", overlay).value.trim(),
      subject,
      description,
      category: $("#nt-cat", overlay).value,
      priority: $("#nt-prio", overlay).value,
      status: "OPEN",
      createdAt: now,
      updatedAt: now
    };
    try {
      await setDoc(doc(state.db, "tickets", id), ticket);
      if (attachment) {
        await addDoc(collection(state.db, "tickets", id, "comments"), {
          authorId: state.fbUser.uid,
          authorName: requesterName,
          content: "",
          imageUrl: attachment,
          timestamp: now
        }).catch(() => toast("Ticket created but the photo couldn't be attached.", true));
      }
      if (!state.tickets.some(t => t.id === id)) {
        state.tickets = [ticket, ...state.tickets];
      }
      overlay.remove();
      toast("Ticket #" + ticket.ticketNumber + " created");
      nav("#/ticket/" + id);
    } catch (err) {
      toast(err.message, true);
      btn.disabled = false;
      btn.textContent = "Submit ticket";
    }
  });
}

function openEditProfileDialog() {
  const p = state.profile || {};
  const overlay = dialogShell(`
    <h3>Edit profile</h3>
    <form id="ep-form">
      <div class="field">
        <label for="ep-name">Full name</label>
        <input id="ep-name" type="text" value="${esc(p.fullName || "")}" required>
      </div>
      <div class="field">
        <label for="ep-phone">Phone</label>
        <input id="ep-phone" type="tel" value="${esc(p.phoneNumber || "")}">
      </div>
      <div class="field">
        <label for="ep-loc">Location</label>
        <input id="ep-loc" type="text" value="${esc(p.location || "")}">
      </div>
      <div class="field">
        <label for="ep-bio">Bio</label>
        <textarea id="ep-bio" placeholder="A few words about you">${esc(p.bio || "")}</textarea>
      </div>
      <div class="row-btns">
        <button type="button" class="btn ghost" id="ep-cancel">Cancel</button>
        <button type="submit" class="btn">Save changes</button>
      </div>
    </form>`);

  $("#ep-cancel", overlay).addEventListener("click", () => overlay.remove());
  $("#ep-form", overlay).addEventListener("submit", async (e) => {
    e.preventDefault();
    const fullName = $("#ep-name", overlay).value.trim();
    const phoneNumber = $("#ep-phone", overlay).value.trim();
    const location = $("#ep-loc", overlay).value.trim();
    const bio = $("#ep-bio", overlay).value.trim();
    if (!fullName) return;
    try {
      await updateDoc(doc(state.db, "users", state.fbUser.uid), { fullName, phoneNumber, location, bio });
      if (fullName !== (state.fbUser.displayName || "")) {
        await updateProfile(state.fbUser, { displayName: fullName }).catch(() => {});
      }
      overlay.remove();
      toast("Profile updated");
    } catch (err) {
      toast(err.message, true);
    }
  });
}

function initials(name) {
  return (name || "?")
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map(w => w.charAt(0).toUpperCase())
    .join("") || "?";
}

function renderSettingsCard() {
  const prefs = notifPrefs();
  const toggle = (key, title, sub) => `
    <div class="toggle-row">
      <div class="tg-body"><b>${title}</b><span>${sub}</span></div>
      <span class="switch">
        <input type="checkbox" data-pref="${key}" ${prefs[key] ? "checked" : ""} aria-label="${title}">
        <span class="track"></span><span class="thumb"></span>
      </span>
    </div>`;
  return `
    <div class="card settings-card">
      <h4>Notification settings</h4>
      ${toggle("status", "Status changes", "When a ticket moves through the workflow")}
      ${toggle("comments", "Replies", "When support responds on your tickets")}
      ${toggle("priority", "Priority alerts", "High and critical tickets needing attention")}
      <div class="toggle-row">
        <div class="tg-body"><b>Help &amp; support</b><span>Questions, feedback or account issues</span></div>
        <button class="btn ghost small" id="help-btn">Contact</button>
      </div>
      <div class="toggle-row">
        <div class="tg-body"><b>About NextHelp</b><span>Version 1.0 · Android &amp; Web</span></div>
        <button class="btn ghost small" id="about-btn">View</button>
      </div>
    </div>`;
}

function bindSettingsCard(root) {
  $$("#app input[data-pref]", root).forEach((input) => {
    input.addEventListener("change", () => {
      setNotifPref(input.dataset.pref, input.checked);
      toast("Preference saved");
      scheduleRender();
    });
  });
  $("#help-btn", root)?.addEventListener("click", () => {
    const overlay = dialogShell(`
      <h3>Help &amp; support</h3>
      <p class="muted" style="font-size:13.5px;line-height:1.6">Need a hand? Reach the NextHelp team and we'll get back to you quickly.</p>
      <div class="kv" style="margin-top:12px">
        <dt>Email</dt><dd><a href="mailto:support@nexthelp.app">support@nexthelp.app</a></dd>
        <dt>Response time</dt><dd>Usually within one business day</dd>
      </div>
      <div class="row-btns">
        <a class="btn" href="mailto:support@nexthelp.app">Email support</a>
        <button class="btn ghost" id="hs-close">Close</button>
      </div>`);
    $("#hs-close", overlay).addEventListener("click", () => overlay.remove());
  });
  $("#about-btn", root)?.addEventListener("click", () => {
    const overlay = dialogShell(`
      <h3>About NextHelp</h3>
      <div style="display:flex;flex-direction:column;align-items:center;gap:10px;margin-bottom:14px">
        <img src="logo.png" alt="" width="72" height="72" style="border-radius:18px">
        <strong style="font-size:17px">NextHelp</strong>
        <span class="muted" style="font-size:12.5px">Version 1.0 · Web &amp; Android</span>
      </div>
      <p class="muted" style="font-size:13.5px;line-height:1.6;text-align:center">
        Support ticketing that keeps your team and customers in sync — create, assign,
        and resolve tickets with real-time updates on every device.
      </p>
      <div class="row-btns">
        <button class="btn" id="ab-close">Close</button>
      </div>`);
    $("#ab-close", overlay).addEventListener("click", () => overlay.remove());
  });
}

function avatarHtml(url, name, cls = "", editBtn = false) {
  return `
    <span class="avatar ${cls}">
      ${url ? `<img src="${esc(url)}" alt="">` : esc(initials(name))}
      ${editBtn ? '<button class="avatar-edit" id="avatar-edit" title="Change photo">' + ICONS.camera + '</button>' : ""}
    </span>`;
}

function renderProfile(root) {
  const p = state.profile || {};
  const tickets = state.tickets;
  const resolved = tickets.filter(t => ["RESOLVED", "CLOSED"].includes(t.status)).length;

  root.innerHTML = `
    <div class="shell">
    ${navLinks("profile")}
    <main class="main">
      <div class="content">
        <div class="profile-head">
          ${avatarHtml(p.profileImageUrl, p.fullName, "", true)}
          <h2>${esc(p.fullName || "Unnamed user")}</h2>
          <div class="email">${esc(p.email || state.fbUser.email || "")}</div>
          <div><span class="role-chip">${esc(roleLabel())}</span></div>
        </div>
        <div class="pstats">
          <div><b>${tickets.length}</b><span>Tickets</span></div>
          <div><b>${resolved}</b><span>Resolved</span></div>
          <div><b>${tickets.length - resolved}</b><span>Active</span></div>
          <div><b>${conversations().length}</b><span>Threads</span></div>
        </div>
        <div class="profile-actions">
          <button class="btn ghost small" id="edit-profile-btn">Edit profile</button>
          <button class="btn danger small" id="signout-btn">Sign out</button>
        </div>
        <div class="card info-card">
          <div class="info-row">
            <span class="ic" style="color:var(--accent)">${ICONS.alert.replace('width="20" height="20"', 'width="16" height="16"')}</span>
            <div><div class="lbl">Email</div><div class="val">${esc(p.email || "—")}</div></div>
          </div>
          <div class="info-row">
            <span class="ic" style="color:var(--purple)">${ICONS.user.replace('width="20" height="20"', 'width="16" height="16"')}</span>
            <div><div class="lbl">Phone</div><div class="val">${esc(p.phoneNumber || "Not set")}</div></div>
          </div>
          <div class="info-row">
            <span class="ic" style="color:var(--amber)">${ICONS.clock.replace('width="20" height="20"', 'width="16" height="16"')}</span>
            <div><div class="lbl">Location</div><div class="val">${esc(p.location || "Not set")}</div></div>
          </div>
          ${p.bio ? `
          <div class="info-row">
            <span class="ic" style="color:var(--green)">${ICONS.check.replace('width="20" height="20"', 'width="16" height="16"')}</span>
            <div><div class="lbl">Bio</div><div class="val">${esc(p.bio)}</div></div>
          </div>` : ""}
        </div>
        ${renderSettingsCard()}
        <div class="footer-note">NextHelp · <a href="download.html">Get the Android app</a></div>
      </div>
    </main>
    </div>
    ${bottomNav("profile")}`;
  bindShell(root);
  bindSettingsCard(root);

  $("#avatar-edit", root).addEventListener("click", () => {
    const fi = document.createElement("input");
    fi.type = "file";
    fi.accept = "image/*";
    fi.onchange = async () => {
      const file = fi.files?.[0];
      if (!file) return;
      toast("Uploading photo…");
      try {
        const dataUrl = await compressImage(file, 512, 0.82);
        await updateDoc(doc(state.db, "users", state.fbUser.uid), { profileImageUrl: dataUrl });
        toast("Profile photo updated");
      } catch (err) {
        toast(err.message, true);
      }
    };
    fi.click();
  });
  $("#edit-profile-btn", root).addEventListener("click", openEditProfileDialog);
  $("#signout-btn", root).addEventListener("click", doSignOut);
}

function render() {
  const root = $("#app");
  if (!root || !state.ready) return;

  if (!state.fbUser) {
    renderAuth(root);
    return;
  }

  if (state.route.name === "inbox") localStorage.setItem(lastSeenKey(), String(Date.now()));

  switch (state.route.name) {
    case "tickets": renderTickets(root); break;
    case "detail": renderDetail(root); break;
    case "inbox": renderInbox(root); break;
    case "profile": renderProfile(root); break;
    default: renderHome(root);
  }
}

async function main() {
  const cfg = await loadConfig();
  if (!cfg) {
    renderConfigError($("#app"));
    return;
  }

  const app = initializeApp(cfg);
  state.auth = getAuth(app);
  state.db = initializeFirestore(app, { experimentalAutoDetectLongPolling: true });

  onAuthStateChanged(state.auth, async (fbUser) => {
    const previousUid = state.fbUser?.uid;
    state.fbUser = fbUser;
    state.ready = true;

    if (fbUser) {
      if (previousUid !== fbUser.uid) {
        await ensureProfile(fbUser, fbUser.displayName).catch(() => {});
        watchProfile(fbUser.uid);
        restartTicketsStream();
        state.route = parseHash();
        if (state.route.name === "detail") openDetail(state.route.id);
      }
    } else {
      stopStreams();
      stopDetailComments();
      state.route = { name: "home" };
    }
    scheduleRender();
  });
}

main();
