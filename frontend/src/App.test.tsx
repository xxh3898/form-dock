import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import App from './App.tsx'

describe('App', () => {
  it('should_renderScaffoldBoundary_when_applicationStarts', () => {
    render(<App />)

    expect(
      screen.getByRole('heading', { name: 'Application scaffold' }),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/Business features have not been implemented yet\./),
    ).toBeInTheDocument()
  })
})
