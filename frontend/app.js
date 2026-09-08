'use strict';

const API = '/api/v1';
const HEARTBEAT_MS = 10_000;
const PRESENCE_MS = 5_000;
const PAGE_SIZE = 20;

async function call(path, {method = 'GET', body, token} = {}) {
  const response = await fetch(API + path, {
    method,
    headers: {
      ...(body ? {'Content-Type': 'application/json'} : {}),
      ...(token ? {Authorization: `Bearer ${token}`} : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new Error(payload?.detail || payload?.title || `HTTP ${response.status}`);
  }
  return payload;
}

/** Свой же id нужен, чтобы отличать свои сообщения от чужих и слать heartbeat. */
function userIdOf(token) {
  const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(atob(payload.padEnd(payload.length + ((4 - (payload.length % 4)) % 4), '='))).sub;
}

function time(iso) {
  return new Date(iso).toLocaleTimeString('ru-RU', {hour: '2-digit', minute: '2-digit'});
}

class Panel {
  constructor(root) {
    this.root = root;
    this.role = root.dataset.role;
    this.messages = new Map();
    this.conversation = null;
    this.stomp = null;
    this.subscription = null;
    this.timers = {};

    this.ui = {
      login: root.querySelector('.login'),
      register: root.querySelector('.register'),
      signedIn: root.querySelector('.signed-in'),
      whoami: root.querySelector('.whoami'),
      newConversation: root.querySelector('.new-conversation'),
      conversations: root.querySelector('.conversations'),
      chat: root.querySelector('.chat'),
      chatTitle: root.querySelector('.chat-title'),
      presence: root.querySelector('.presence'),
      loadMore: root.querySelector('.load-more'),
      messages: root.querySelector('.messages'),
      send: root.querySelector('.send'),
      status: root.querySelector('.status'),
    };

    this.ui.login.addEventListener('submit', (event) => this.guard(event, () => this.login()));
    this.ui.register?.addEventListener('click', () => this.guard(null, () => this.register()));
    this.ui.newConversation?.addEventListener('submit', (event) => this.guard(event, () => this.createConversation()));
    this.ui.send.addEventListener('submit', (event) => this.guard(event, () => this.sendMessage()));
    this.ui.loadMore.addEventListener('click', () => this.guard(null, () => this.loadOlder()));
  }

  async guard(event, action) {
    event?.preventDefault();
    try {
      await action();
      this.say('');
    } catch (failure) {
      this.say(failure.message, true);
    }
  }

  say(text, isError = false) {
    this.ui.status.textContent = text;
    this.ui.status.classList.toggle('error', isError);
  }

  credentials() {
    const form = new FormData(this.ui.login);
    return {email: form.get('email'), password: form.get('password')};
  }

  async register() {
    await call('/auth/register', {method: 'POST', body: this.credentials()});
    await this.login();
  }

  async login() {
    const {accessToken} = await call('/auth/login', {method: 'POST', body: this.credentials()});
    this.token = accessToken;
    this.userId = userIdOf(accessToken);
    this.ui.login.hidden = true;
    this.ui.signedIn.hidden = false;
    this.ui.whoami.textContent = `${this.role === 'CLIENT' ? 'Клиент' : 'Оператор'} ${this.userId}`;
    this.connect();
    await this.loadConversations();
  }

  connect() {
    this.stomp = new StompJs.Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: {Authorization: `Bearer ${this.token}`},
      reconnectDelay: 3000,
      onConnect: () => this.onConnected(),
      onWebSocketClose: () => this.say('Соединение потеряно, переподключаемся…'),
      onStompError: (frame) => this.say(frame.headers.message || 'STOMP отказал', true),
    });
    this.stomp.activate();
  }

  /**
   * Вызывается и после обрыва: подписки поднимаются заново, а история дочитывается —
   * pub/sub доставляет at-most-once, пропущенное лежит только в базе.
   */
  onConnected() {
    this.say('Подключено');
    clearInterval(this.timers.heartbeat);
    this.timers.heartbeat = setInterval(() => this.stomp.publish({destination: '/app/presence/heartbeat'}), HEARTBEAT_MS);
    this.stomp.publish({destination: '/app/presence/heartbeat'});
    if (this.role === 'OPERATOR') {
      this.stomp.subscribe('/topic/queue', (frame) => this.onQueueEvent(JSON.parse(frame.body)));
    }
    if (this.conversation) {
      this.subscribeToConversation();
      this.guard(null, () => this.loadNewest());
    }
    this.guard(null, () => this.loadConversations());
  }

  async loadConversations() {
    const query = this.role === 'OPERATOR' ? '?status=WAITING' : '';
    this.renderConversations(await call(`/conversations${query}`, {token: this.token}));
  }

  onQueueEvent(conversation) {
    if (conversation.status !== 'WAITING') {
      return;
    }
    this.renderConversation(conversation);
    this.say(`Новое обращение: ${conversation.topic}`);
  }

  renderConversations(conversations) {
    this.ui.conversations.replaceChildren();
    conversations.forEach((conversation) => this.renderConversation(conversation));
  }

  renderConversation(conversation) {
    const existing = this.ui.conversations.querySelector(`[data-id="${conversation.id}"]`);
    existing?.remove();

    const row = document.createElement('li');
    row.dataset.id = conversation.id;
    const label = document.createElement('span');
    label.textContent = `${conversation.topic} — ${conversation.status}`;
    const open = document.createElement('button');
    open.type = 'button';
    open.textContent = this.role === 'OPERATOR' && conversation.status === 'WAITING' ? 'Взять' : 'Открыть';
    open.addEventListener('click', () =>
      this.guard(null, async () => {
        const opened =
          this.role === 'OPERATOR' && conversation.status === 'WAITING'
            ? await this.take(conversation)
            : conversation;
        await this.openConversation(opened);
      }));
    row.append(label, open);
    this.ui.conversations.prepend(row);
  }

  /** Обращение мог забрать другой оператор: 409 — это не сбой, а проигранная гонка. */
  async take(conversation) {
    try {
      return await call(`/conversations/${conversation.id}/take`, {method: 'POST', token: this.token});
    } catch (failure) {
      this.ui.conversations.querySelector(`[data-id="${conversation.id}"]`)?.remove();
      throw failure;
    }
  }

  async createConversation() {
    const form = new FormData(this.ui.newConversation);
    const conversation = await call('/conversations', {
      method: 'POST',
      token: this.token,
      body: {topic: form.get('topic')},
    });
    this.ui.newConversation.reset();
    await this.openConversation(conversation);
  }

  async openConversation(conversation) {
    this.messages.clear();
    this.ui.messages.replaceChildren();
    this.ui.chat.hidden = false;
    this.showConversation(conversation);
    this.subscribeToConversation();
    await this.loadNewest();
    clearInterval(this.timers.presence);
    this.timers.presence = setInterval(() => this.guard(null, () => this.refreshPresence()), PRESENCE_MS);
    await this.refreshPresence();
  }

  showConversation(conversation) {
    this.conversation = conversation;
    this.ui.chatTitle.textContent = `Обращение «${conversation.topic}» (${conversation.status})`;
    this.renderConversation(conversation);
  }

  /**
   * Взятие обращения событием не рассылается — оно уходит только операторам в очередь.
   * Клиент узнаёт своего оператора отсюда, тем же опросом, что и статусы.
   */
  async syncConversation() {
    const query = this.role === 'OPERATOR' ? '?mine=true' : '';
    const conversations = await call(`/conversations${query}`, {token: this.token});
    const fresh = conversations.find((conversation) => conversation.id === this.conversation.id);
    if (fresh) {
      this.showConversation(fresh);
    }
  }

  subscribeToConversation() {
    this.subscription?.unsubscribe();
    this.subscription = this.stomp.subscribe(`/topic/conversations/${this.conversation.id}`, (frame) =>
      this.showMessages([JSON.parse(frame.body)]));
  }

  loadNewest() {
    return this.loadHistory(null);
  }

  loadOlder() {
    const oldest = Math.min(...this.messages.keys());
    return this.loadHistory(Number.isFinite(oldest) ? oldest : null);
  }

  async loadHistory(before) {
    const query = new URLSearchParams({limit: PAGE_SIZE});
    if (before !== null) {
      query.set('before', before);
    }
    const page = await call(`/conversations/${this.conversation.id}/messages?${query}`, {token: this.token});
    this.showMessages(page);
    this.ui.loadMore.hidden = page.length < PAGE_SIZE;
  }

  /** Ключ — id сообщения: дубли от переподписки и дочитывания истории отсекаются здесь. */
  showMessages(messages) {
    messages.forEach((message) => this.messages.set(message.id, message));
    this.ui.messages.replaceChildren(
      ...[...this.messages.values()]
        .sort((left, right) => left.id - right.id)
        .map((message) => {
          const row = document.createElement('li');
          row.classList.toggle('mine', message.senderId === this.userId);
          row.textContent = message.text;
          const at = document.createElement('time');
          at.textContent = time(message.sentAt);
          row.append(at);
          return row;
        }));
    this.ui.messages.scrollTop = this.ui.messages.scrollHeight;
  }

  sendMessage() {
    const form = new FormData(this.ui.send);
    this.stomp.publish({
      destination: `/app/conversations/${this.conversation.id}/send`,
      body: JSON.stringify({text: form.get('text')}),
    });
    this.ui.send.reset();
  }

  counterpartId() {
    return this.role === 'CLIENT' ? this.conversation.operatorId : this.conversation.clientId;
  }

  async refreshPresence() {
    if (!this.counterpartId()) {
      await this.syncConversation();
    }
    const counterpart = this.counterpartId();
    if (!counterpart) {
      this.ui.presence.textContent = 'оператор ещё не назначен';
      this.ui.presence.className = 'presence';
      return;
    }
    const {statuses} = await call(`/presence?userIds=${counterpart}`, {token: this.token});
    const online = statuses[counterpart];
    this.ui.presence.textContent = online ? 'онлайн' : 'оффлайн';
    this.ui.presence.className = `presence ${online ? 'online' : 'offline'}`;
  }
}

document.querySelectorAll('.panel').forEach((root) => new Panel(root));
