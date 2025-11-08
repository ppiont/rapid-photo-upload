import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '@/contexts/AuthContext'

export function Header() {
  const { user, isAuthenticated, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <header className="app-header">
      <div className="header-container">
        <Link to="/" className="logo">
          RapidPhotoUpload
        </Link>

        {isAuthenticated && (
          <nav className="nav-menu">
            <Link to="/" className="nav-link">
              Upload
            </Link>
            <Link to="/gallery" className="nav-link">
              Gallery
            </Link>
          </nav>
        )}

        <div className="header-right">
          {isAuthenticated && (
            <>
              <span className="user-email">{user?.email}</span>
              <button onClick={handleLogout} className="btn-secondary btn-small">
                Logout
              </button>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
