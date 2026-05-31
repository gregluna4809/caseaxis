import { render, screen, within } from '@testing-library/react';
import { App } from '../App';

describe('App', () => {
  it('renders non-empty document body', () => {
    render(<App />);

    expect(document.body).not.toBeEmptyDOMElement();
  });

  it('displays public demo access instructions on the login page', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: 'Enterprise case workflow platform' })).toBeInTheDocument();
    expect(screen.getByText('75,000')).toBeInTheDocument();
    expect(screen.getByText('seeded cases')).toBeInTheDocument();
    expect(screen.getByText('RBAC')).toBeInTheDocument();
    expect(screen.getByText('+ audit logging')).toBeInTheDocument();
    expect(screen.getByText('Production')).toBeInTheDocument();
    expect(screen.getByText('smoke-tested')).toBeInTheDocument();

    const demoPanel = screen.getByRole('region', { name: 'Live Demo Access' });
    expect(within(demoPanel).getByRole('heading', { name: 'Live Demo Access' })).toBeInTheDocument();
    expect(within(demoPanel).getByText('Username')).toBeInTheDocument();
    expect(within(demoPanel).getByText('demo')).toBeInTheDocument();
    expect(within(demoPanel).getByText('Password')).toBeInTheDocument();
    expect(within(demoPanel).getByText('demo123')).toBeInTheDocument();
    expect(within(demoPanel).getByText('This environment contains synthetic demonstration data. Actions performed using the demo account may be visible to other visitors and may generate audit events.')).toBeInTheDocument();
  });
});
