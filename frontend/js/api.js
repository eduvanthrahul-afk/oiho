const API = (() => {
  const TOKEN_KEY = 'decisiontwin.access';
  const REFRESH_KEY = 'decisiontwin.refresh';
  const getToken = () => localStorage.getItem(TOKEN_KEY);
  const setTokens = (tokens) => {
    localStorage.setItem(TOKEN_KEY, tokens.accessToken);
    localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
  };
  const clear = () => { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(REFRESH_KEY); };
  async function request(path, options = {}) {
    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    if (getToken()) headers.Authorization = `Bearer ${getToken()}`;
    let response = await fetch(`/api${path}`, { ...options, headers });
    const mayRefresh = !['/auth/login', '/auth/register', '/auth/refresh'].includes(path);
    if (response.status === 401 && localStorage.getItem(REFRESH_KEY) && mayRefresh) {
      const refreshed = await fetch('/api/auth/refresh', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: localStorage.getItem(REFRESH_KEY) }) });
      if (refreshed.ok) { setTokens(await refreshed.json()); headers.Authorization = `Bearer ${getToken()}`; response = await fetch(`/api${path}`, { ...options, headers }); }
      else clear();
    }
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.message || body.error || `Request failed (${response.status})`);
    }
    if (response.status === 204) return null;
    return response.json();
  }
  return {
    hasSession: () => !!getToken(), clear,
    async login(email, password) { const data = await request('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }); setTokens(data); return data; },
    async register(name, email, password) { const data = await request('/auth/register', { method: 'POST', body: JSON.stringify({ name, email, password }) }); setTokens(data); return data; },
    me: () => request('/auth/me'),
    logout: () => request('/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken: localStorage.getItem(REFRESH_KEY) }) }).finally(clear),
    decisions: () => request('/decisions'),
    decision: (id) => request(`/decisions/${id}`),
    createDecision: (payload) => request('/decisions', { method: 'POST', body: JSON.stringify(payload) }),
    runSimulation: (payload) => request('/simulations', { method: 'POST', body: JSON.stringify(payload) }),
    retrySimulation: (id) => request(`/simulations/${id}/retry`, { method: 'POST' }),
    simulation: (id) => request(`/simulations/${id}`),
    async followSimulation(id, onEvent) {
      const response = await fetch(`/api/simulations/${id}/events`, { headers: { Authorization: `Bearer ${getToken()}`, Accept: 'text/event-stream' } });
      if (!response.ok || !response.body) throw new Error('Could not connect to simulation progress.');
      const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = ''; let stopped = false;
      while (!stopped) {
        const { value, done } = await reader.read(); if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const chunks = buffer.split(/\r?\n\r?\n/); buffer = chunks.pop() || '';
        for (const chunk of chunks) {
          const eventName = chunk.match(/^event:\s*(.+)$/m)?.[1] || '';
          const dataLine = chunk.split(/\r?\n/).find(line => line.startsWith('data:'));
          if (!dataLine) continue;
          const event = JSON.parse(dataLine.slice(5).trim()); onEvent(event);
          if (eventName === 'simulation.completed' || eventName === 'simulation.failed') stopped = true;
        }
      }
      return true;
    },
  };
})();
