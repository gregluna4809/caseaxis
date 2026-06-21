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
      login(data.username, data.roles);
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed. Check your credentials.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-shell">
      <header className="login-agency-bar" aria-label="MBRA portal identity">
        <div className="login-agency-inner">
          <div className="login-agency-mark" aria-hidden="true">MBRA</div>
          <div>
            <p className="login-agency-eyebrow">Metropolitan Benefits Review Authority</p>
            <p className="login-agency-name">Staff Operations Portal</p>
          </div>
        </div>
      </header>

      <div className="login-landing">
        <section className="login-hero-panel" aria-labelledby="login-hero-title">
          <div className="login-hero-copy">
            <p className="page-kicker">MBRA</p>
            <h1 id="login-hero-title" className="login-hero-title">Every Benefit Review Matters.</h1>
            <p className="login-hero-subtitle">
              Supporting fair, timely, and careful benefit reviews for residents and families across the metropolitan service area.
            </p>
          </div>

          <div className="login-service-principles" aria-label="Service principles">
            <p>Respectful service</p>
            <p>Reliable decisions</p>
            <p>Accessible support</p>
          </div>
        </section>

        <div className="login-card">
          <div className="login-header">
            <div className="login-logo">MBRA</div>
            <h2 className="login-title">Staff login</h2>
            <p className="login-subtitle">Benefit Review Management System</p>
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
              <span className="demo-access-marker" aria-hidden="true">D</span>
              <div>
                <h3 id="demo-access-title">Demo access</h3>
                <p>Use the synthetic MBRA training environment.</p>
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
              This environment contains synthetic case review data. Actions performed using the demo account may be visible to other visitors and may appear in the service history.
            </p>
          </section>
        </div>
      </div>
    </div>
  );
}
