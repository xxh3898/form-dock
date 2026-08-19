import { Link } from 'react-router'

function NotFoundPage() {
  return (
    <main className="auth-shell">
      <section aria-labelledby="not-found-title" className="auth-card">
        <p className="product-name">FormDock</p>
        <h1 id="not-found-title">Page not found</h1>
        <p className="page-description">
          The requested FormDock page is not available.
        </p>
        <Link className="text-link" to="/">
          Go to Creator entry
        </Link>
      </section>
    </main>
  )
}

export default NotFoundPage
