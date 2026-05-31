import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../lib/apiClient';
import { useAuth } from '../context/AuthContext';

export function LoginPage() {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  if (isAuthenticated) {
    navigate('/dashboard', { replace: true });
    return null;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!username.trim() || !password.trim()) {
      setError('Username and password are required.');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const data = await api.auth.login(username.trim(), password);
      login(data.token, username.trim());
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed. Check your credentials.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-shell">
      <div className="login-landing">
        <section className="login-hero-panel" aria-labelledby="login-hero-title">
          <div className="login-hero-copy">
            <div className="login-logo hero-logo">CA</div>
            <p className="page-kicker">CaseAxis</p>
            <h1 id="login-hero-title" className="login-hero-title">Enterprise case workflow platform</h1>
            <p className="login-hero-subtitle">
              Production-style case operations with realistic workflow data, secure access controls, and auditable activity.
            </p>
          </div>

          <div className="login-metric-grid" aria-label="Platform highlights">
            <div className="login-metric-tile">
              <strong>75,000</strong>
              <span>seeded cases</span>
            </div>
            <div className="login-metric-tile">
              <strong>RBAC</strong>
              <span>+ audit logging</span>
            </div>
            <div className="login-metric-tile">
              <strong>Production</strong>
              <span>smoke-tested</span>
            </div>
          </div>

          <div className="login-preview-panel" aria-hidden="true">
            <div className="preview-toolbar">
              <span></span>
              <span></span>
              <span></span>
            </div>
            <div className="preview-metrics">
              <span></span>
              <span></span>
              <span></span>
            </div>
            <div className="preview-workflow">
              <span className="workflow-node active"></span>
              <span className="workflow-line"></span>
              <span className="workflow-node"></span>
              <span className="workflow-line"></span>
              <span className="workflow-node"></span>
            </div>
            <div className="preview-rows">
              <span></span>
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
        </section>

        <div className="login-card">
          <div className="login-header">
            <div className="login-logo">CA</div>
            <h2 className="login-title">Sign in to CaseAxis</h2>
            <p className="login-subtitle">Enterprise case workflow</p>
          </div>

          <form className="login-form" onSubmit={handleSubmit} noValidate>
            {error && <div className="form-error">{error}</div>}

            <div className="form-group">
              <label className="form-label" htmlFor="username">Username</label>
              <input
                id="username"
                type="text"
                className="form-input"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                autoFocus
                disabled={loading}
              />
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="password">Password</label>
              <input
                id="password"
                type="password"
                className="form-input"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                disabled={loading}
              />
            </div>

            <button type="submit" className="btn btn-primary w-full login-submit" disabled={loading}>
              {loading ? 'Signing in...' : 'Sign in'}
            </button>
          </form>

          <section className="demo-access-panel" aria-labelledby="demo-access-title">
            <div className="demo-access-heading">
              <span className="demo-access-marker" aria-hidden="true">
                <svg viewBox="0 0 24 24" focusable="false">
                  <path d="M7 17 17 7" />
                  <path d="M9 7h8v8" />
                </svg>
              </span>
              <div>
                <h3 id="demo-access-title">Live Demo Access</h3>
                <p>Explore the public portfolio environment.</p>
              </div>
            </div>

            <dl className="demo-credentials">
              <div>
                <dt>Username</dt>
                <dd>demo</dd>
              </div>
              <div>
                <dt>Password</dt>
                <dd>demo123</dd>
              </div>
            </dl>

            <p className="demo-access-note">
              This environment contains synthetic demonstration data. Actions performed using the demo account may be visible to other visitors and may generate audit events.
            </p>
          </section>
        </div>
      </div>
    </div>
  );
}
