import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { OAuthCallbackPage } from './pages/OAuthCallbackPage'
import { TeamDetailPage } from './pages/TeamDetailPage'
import { CharactersPage } from './pages/account/CharactersPage'
import { MyTeamsPage } from './pages/account/MyTeamsPage'
import { CreateTeamPage } from './pages/account/CreateTeamPage'
import { ProtectedRoute } from './components/ProtectedRoute'

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Área pública */}
        <Route path="/" element={<HomePage />} />
        <Route path="/teams/:id" element={<TeamDetailPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/oauth/callback" element={<OAuthCallbackPage />} />

        {/* Área logada */}
        <Route
          path="/account/characters"
          element={
            <ProtectedRoute>
              <CharactersPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/account/teams"
          element={
            <ProtectedRoute>
              <MyTeamsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/account/teams/new"
          element={
            <ProtectedRoute>
              <CreateTeamPage />
            </ProtectedRoute>
          }
        />
      </Routes>
    </BrowserRouter>
  )
}
