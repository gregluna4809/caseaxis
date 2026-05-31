import { render, screen, within } from '@testing-library/react';
import { App } from '../App';

describe('App', () => {
  it('renders non-empty document body', () => {
    render(<App />);

    expect(document.body).not.toBeEmptyDOMElement();
  });

  it('displays public demo access instructions on the login page', () => {
    render(<App />);

    const demoPanel = screen.getByRole('region', { name: 'Demo Access' });
    expect(within(demoPanel).getByRole('heading', { name: 'Demo Access' })).toBeInTheDocument();
    expect(within(demoPanel).getByText('Username')).toBeInTheDocument();
    expect(within(demoPanel).getByText('demo')).toBeInTheDocument();
    expect(within(demoPanel).getByText('Password')).toBeInTheDocument();
    expect(within(demoPanel).getByText('demo123')).toBeInTheDocument();
    expect(within(demoPanel).getByText('Please do not enter real personal, financial, medical, or confidential information.')).toBeInTheDocument();
  });
});
