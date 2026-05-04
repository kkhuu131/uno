import { Route, Routes, useLocation, useParams } from 'react-router-dom'
import { Home } from './components/Home'
import { LobbyRoom } from './components/LobbyRoom'
import { GameTable } from './components/GameTable'

function GameRoute() {
  const { gameId } = useParams<{ gameId: string }>()
  const location = useLocation()
  const statePlayerIndex = (location.state as { playerIndex?: number } | null)?.playerIndex
  const localPlayerIndex =
    statePlayerIndex ??
    parseInt(sessionStorage.getItem('uno_player_index') ?? '0', 10)

  if (!gameId) return <div>Invalid game URL</div>
  return <GameTable gameId={gameId} localPlayerIndex={localPlayerIndex} />
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/lobby/:code" element={<LobbyRoom />} />
      <Route path="/game/:gameId" element={<GameRoute />} />
    </Routes>
  )
}
