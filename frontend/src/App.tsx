import { useState } from 'react'
import { GameSetup } from './components/GameSetup'
import { GameTable } from './components/GameTable'

export default function App() {
  const [gameId, setGameId] = useState<string | null>(null)

  return gameId ? (
    <GameTable gameId={gameId} onNewGame={() => setGameId(null)} />
  ) : (
    <GameSetup onGameCreated={setGameId} />
  )
}
