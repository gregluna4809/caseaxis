import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ProtectedRoute } from './components/ProtectedRoute';
import { AppShell } from './components/AppShell';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { CaseListPage } from './pages/CaseListPage';
import { CreateCasePage } from './pages/CreateCasePage';
import { CaseDetailPage } from './pages/CaseDetailPage';
import { ClientListPage } from './pages/ClientListPage';
import { ClientDetailPage } from './pages/ClientDetailPage';
import { OrgListPage } from './pages/OrgListPage';
import { OrgDetailPage } from './pages/OrgDetailPage';

function PlaceholderPage({ title }: { title: string }) {
  return (
    <div className="page-stack">
      <div className="page-header">
        <h1 className="page-title">{title}</h1>
        <p className="page-subtitle">This module is coming soon.</p>
      </div>
    </div>
  );
}

export function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />

          <Route element={<ProtectedRoute />}>
            <Route element={<AppShell />}>
              <Route index element={<Navigate to="/dashboard" replace />} />
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/cases" element={<CaseListPage />} />
              <Route path="/cases/new" element={<CreateCasePage />} />
              <Route path="/cases/:id" element={<CaseDetailPage />} />
              <Route path="/clients" element={<ClientListPage />} />
              <Route path="/clients/:id" element={<ClientDetailPage />} />
              <Route path="/organizations" element={<OrgListPage />} />
              <Route path="/organizations/:id" element={<OrgDetailPage />} />
              <Route path="/tasks" element={<PlaceholderPage title="Tasks" />} />
              <Route path="/reports" element={<PlaceholderPage title="Reports" />} />
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
