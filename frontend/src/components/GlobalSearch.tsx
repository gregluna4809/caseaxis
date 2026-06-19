import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../lib/apiClient';
import type { SearchResults } from '../types/api';

export function GlobalSearch() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchResults | null>(null);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  const doSearch = useCallback(async (q: string) => {
    if (!q.trim()) {
      setResults(null);
      setOpen(false);
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const data = await api.search.query(q.trim());
      setResults(data);
      setOpen(true);
    } catch {
      setResults(null);
    } finally {
      setLoading(false);
    }
  }, []);

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const val = e.target.value;
    setQuery(val);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => doSearch(val), 300);
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Escape') {
      setOpen(false);
      inputRef.current?.blur();
    }
  }

  function go(path: string) {
    setOpen(false);
    setQuery('');
    setResults(null);
    navigate(path);
  }

  const hasResults = results && (
    results.cases.length > 0 ||
    results.clients.length > 0 ||
    results.organizations.length > 0 ||
    results.tasks.length > 0
  );

  return (
    <div className="global-search-wrapper" ref={wrapperRef}>
      <div className="global-search">
        <span className="search-icon">Search</span>
        <input
          ref={inputRef}
          className="global-search-input"
          type="text"
          placeholder="Cases, recipients, agencies..."
          value={query}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          onFocus={() => { if (results && query.trim()) setOpen(true); }}
          aria-label="Global search"
          aria-expanded={open}
          aria-haspopup="listbox"
          autoComplete="off"
        />
        {loading && <span className="search-spinner" aria-hidden="true" />}
      </div>

      {open && (
        <div className="search-dropdown" role="listbox" aria-label="Search results">
          {!hasResults && !loading && (
            <div className="search-no-results">No results for &ldquo;{query}&rdquo;</div>
          )}

          {results && results.cases.length > 0 && (
            <div className="search-group">
              <div className="search-group-header">Benefit Cases</div>
              {results.cases.map(item => (
                <div
                  key={item.id}
                  className="search-result-item"
                  role="option"
                  tabIndex={0}
                  onClick={() => go(`/cases/${item.id}`)}
                  onKeyDown={e => e.key === 'Enter' && go(`/cases/${item.id}`)}
                >
                  <div className="search-result-main">
                    <div className="search-result-title">{item.title}</div>
                    <div className="search-result-sub">{item.caseNumber}</div>
                  </div>
                  <span className="search-result-badge">{item.statusDisplayName}</span>
                </div>
              ))}
            </div>
          )}

          {results && results.clients.length > 0 && (
            <div className="search-group">
              <div className="search-group-header">Benefit Recipients</div>
              {results.clients.map(item => (
                <div
                  key={item.id}
                  className="search-result-item"
                  role="option"
                  tabIndex={0}
                  onClick={() => go(`/clients/${item.id}`)}
                  onKeyDown={e => e.key === 'Enter' && go(`/clients/${item.id}`)}
                >
                  <div className="search-result-main">
                    <div className="search-result-title">{item.displayName}</div>
                    <div className="search-result-sub">
                      {item.clientNumber}{item.email ? ` · ${item.email}` : ''}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {results && results.organizations.length > 0 && (
            <div className="search-group">
              <div className="search-group-header">Agencies</div>
              {results.organizations.map(item => (
                <div
                  key={item.id}
                  className="search-result-item"
                  role="option"
                  tabIndex={0}
                  onClick={() => go(`/organizations/${item.id}`)}
                  onKeyDown={e => e.key === 'Enter' && go(`/organizations/${item.id}`)}
                >
                  <div className="search-result-main">
                    <div className="search-result-title">{item.name}</div>
                    <div className="search-result-sub">{item.organizationCode}</div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {results && results.tasks.length > 0 && (
            <div className="search-group">
              <div className="search-group-header">Review Actions</div>
              {results.tasks.map(item => (
                <div
                  key={item.id}
                  className="search-result-item"
                  role="option"
                  tabIndex={0}
                  onClick={() => go(`/tasks/${item.id}`)}
                  onKeyDown={e => e.key === 'Enter' && go(`/tasks/${item.id}`)}
                >
                  <div className="search-result-main">
                    <div className="search-result-title">{item.title}</div>
                    <div className="search-result-sub">Review action</div>
                  </div>
                  <span className="search-result-badge">{item.statusDisplayName}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
