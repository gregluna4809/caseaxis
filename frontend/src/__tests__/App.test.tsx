import { render } from '@testing-library/react';
import { App } from '../App';

describe('App', () => {
  it('renders non-empty document body', () => {
    render(<App />);

    expect(document.body).not.toBeEmptyDOMElement();
  });
});
