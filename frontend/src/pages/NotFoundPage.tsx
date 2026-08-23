import { Link } from 'react-router'

function NotFoundPage() {
  return (
    <main className="auth-shell">
      <section aria-labelledby="not-found-title" className="auth-card">
        <p className="product-name">FormDock</p>
        <h1 id="not-found-title">페이지를 찾을 수 없습니다</h1>
        <p className="page-description">
          요청한 FormDock 페이지가 없습니다.
        </p>
        <Link className="text-link" to="/">
          관리자 화면으로 이동
        </Link>
      </section>
    </main>
  )
}

export default NotFoundPage
