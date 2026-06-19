import { render, screen, within } from '@testing-library/react';
import { App } from '../App';

describe('App', () => {
  it('renders non-empty document body', () => {
    render(<App />);

    expect(document.body).not.toBeEmptyDOMElement();
  });

  it('displays public demo access instructions on the login page', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: 'Metropolitan Benefits Review Authority' })).toBeInTheDocument();
    expect(screen.getByText('Every Case Matters.')).toBeInTheDocument();
    expect(screen.getAllByText('MBRA').length).toBeGreaterThan(0);
    expect(screen.getByText('Review')).toBeInTheDocument();
    expect(screen.getByText('structured queues')).toBeInTheDocument();
    expect(screen.getByText('Decision')).toBeInTheDocument();
    expect(screen.getByText('determination flow')).toBeInTheDocument();
    expect(screen.getByText('Deadline')).toBeInTheDocument();
    expect(screen.getByText('case timeliness')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Case Management System' })).toBeInTheDocument();
    expect(screen.getByText('Staff Operations Portal')).toBeInTheDocument();
    expect(screen.getByText('Case review workflow')).toBeInTheDocument();
    expect(screen.getByText('Determination tracking')).toBeInTheDocument();
    expect(screen.getByText('Deadline management')).toBeInTheDocument();

    const demoPanel = screen.getByRole('region', { name: 'Service Demo Access' });
    expect(within(demoPanel).getByRole('heading', { name: 'Service Demo Access' })).toBeInTheDocument();
    expect(within(demoPanel).getByText('Username')).toBeInTheDocument();
    expect(within(demoPanel).getByText('demo')).toBeInTheDocument();
    expect(within(demoPanel).getByText('Password')).toBeInTheDocument();
    expect(within(demoPanel).getByText('demo123')).toBeInTheDocument();
    expect(within(demoPanel).getByText('This environment contains synthetic benefits review data. Actions performed using the demo account may be visible to other visitors and may appear in the service history.')).toBeInTheDocument();
  });
});
