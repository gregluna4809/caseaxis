import { render, screen, within } from '@testing-library/react';
import { App } from '../App';

describe('App', () => {
  it('renders non-empty document body', () => {
    render(<App />);

    expect(document.body).not.toBeEmptyDOMElement();
  });

  it('displays public demo access instructions on the login page', () => {
    render(<App />);

    expect(screen.getByText('Metropolitan Benefits Review Authority')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Every Benefit Review Matters.' })).toBeInTheDocument();
    expect(screen.getByText('Supporting fair, timely, and careful benefit reviews for residents and families across the metropolitan service area.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Staff login' })).toBeInTheDocument();

    const demoPanel = screen.getByRole('region', { name: 'Demo access' });
    expect(within(demoPanel).getByRole('heading', { name: 'Demo access' })).toBeInTheDocument();
    expect(within(demoPanel).getByText('Username')).toBeInTheDocument();
    expect(within(demoPanel).getByText('demo')).toBeInTheDocument();
    expect(within(demoPanel).getByText('Password')).toBeInTheDocument();
    expect(within(demoPanel).getByText('demo123')).toBeInTheDocument();
    expect(within(demoPanel).getByText('This environment contains synthetic case review data. Actions performed using the demo account may be visible to other visitors and may appear in the service history.')).toBeInTheDocument();
  });
});
