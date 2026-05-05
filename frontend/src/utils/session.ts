const SESSION_ID_KEY = 'uno_session_id'
const DISPLAY_NAME_KEY = 'uno_display_name'

export function getSessionId(): string {
  let id = localStorage.getItem(SESSION_ID_KEY)
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem(SESSION_ID_KEY, id)
  }
  return id
}

export function getDisplayName(): string {
  return localStorage.getItem(DISPLAY_NAME_KEY) ?? ''
}

export function setDisplayName(name: string): void {
  localStorage.setItem(DISPLAY_NAME_KEY, name.trim())
}

const LOBBY_CODE_KEY = 'uno_lobby_code'

export function getLobbyCode(): string | null {
  return sessionStorage.getItem(LOBBY_CODE_KEY)
}

export function setLobbyCode(code: string): void {
  sessionStorage.setItem(LOBBY_CODE_KEY, code)
}

export function clearLobbyCode(): void {
  sessionStorage.removeItem(LOBBY_CODE_KEY)
}
