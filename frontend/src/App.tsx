import './App.css'

import { Navigate, Route, Routes } from 'react-router'

import { authClient, type AuthClient } from './auth/authClient.ts'
import AdminPage from './pages/AdminPage.tsx'
import LoginPage from './pages/LoginPage.tsx'
import NotFoundPage from './pages/NotFoundPage.tsx'

type AppProps = {
  client?: AuthClient
}

function App({ client = authClient }: AppProps) {
  return (
    <Routes>
      <Route element={<Navigate replace to="/admin" />} path="/" />
      <Route element={<LoginPage client={client} />} path="/login" />
      <Route element={<AdminPage client={client} />} path="/admin" />
      <Route element={<NotFoundPage />} path="*" />
    </Routes>
  )
}

export default App
