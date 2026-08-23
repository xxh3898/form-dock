import './App.css'

import { Navigate, Route, Routes } from 'react-router'

import { authClient, type AuthClient } from './auth/authClient.ts'
import AdminPage from './pages/AdminPage.tsx'
import LoginPage from './pages/LoginPage.tsx'
import NotFoundPage from './pages/NotFoundPage.tsx'
import PublicSurveyPage from './pages/PublicSurveyPage.tsx'
import SurveyBuilderPage from './pages/SurveyBuilderPage.tsx'
import SurveyCreatePage from './pages/SurveyCreatePage.tsx'
import SurveyListPage from './pages/SurveyListPage.tsx'
import SurveyPreviewPage from './pages/SurveyPreviewPage.tsx'
import {
  publicSurveyClient,
  type PublicSurveyClient,
} from './public/publicSurveyClient.ts'
import {
  surveyClient,
  type SurveyClient,
} from './surveys/surveyClient.ts'

type AppProps = {
  client?: AuthClient
  publicSurveys?: PublicSurveyClient
  surveys?: SurveyClient
}

function App({
  client = authClient,
  publicSurveys = publicSurveyClient,
  surveys = surveyClient,
}: AppProps) {
  return (
    <Routes>
      <Route element={<Navigate replace to="/admin" />} path="/" />
      <Route element={<LoginPage client={client} />} path="/login" />
      <Route
        element={<PublicSurveyPage client={publicSurveys} />}
        path="/s/:slug"
      />
      <Route element={<AdminPage client={client} />} path="/admin">
        <Route element={<Navigate replace to="/admin/surveys" />} index />
        <Route
          element={<SurveyListPage client={surveys} />}
          path="surveys"
        />
        <Route
          element={<SurveyCreatePage client={surveys} />}
          path="surveys/new"
        />
        <Route
          element={<SurveyBuilderPage client={surveys} />}
          path="surveys/:surveyId"
        />
        <Route
          element={<SurveyPreviewPage client={surveys} />}
          path="surveys/:surveyId/preview"
        />
      </Route>
      <Route element={<NotFoundPage />} path="*" />
    </Routes>
  )
}

export default App
