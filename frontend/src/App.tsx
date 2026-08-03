import {
  useCallback, useEffect, useRef, useState, type AnchorHTMLAttributes, type FormEvent,
  type MouseEvent as ReactMouseEvent, type ReactNode,
} from 'react'
import { api, ApiRequestError } from './api'
import type {
  Audit, AuditComparison, AuditPage, AuditResults, AuditStatus, Dashboard, DashboardSite, Finding,
  FindingChangeType, FindingSeverity, MaintenanceTask, PortalUser, Role, TaskPage, TaskPriority,
  TaskStatus, TaskUpdateInput, Website, WebsiteInput, WebsitePage,
} from './types'

const roleLabels: Record<Role, string> = {
  ADMIN: 'Администратор',
  SPECIALIST: 'Специалист',
  VIEWER: 'Наблюдатель',
}

const baseNavigation = [
  { to: '/', label: 'Обзор', mark: 'О' },
  { to: '/sites', label: 'Сайты', mark: 'С' },
  { to: '/audits', label: 'Аудиты', mark: 'А' },
  { to: '/tasks', label: 'Задачи', mark: 'З' },
]

function currentLocation() {
  return `${window.location.pathname}${window.location.search}${window.location.hash}`
}

function navigate(to: string, replace = false) {
  const destination = new URL(to, window.location.origin)
  const normalized = `${destination.pathname}${destination.search}${destination.hash}`
  if (normalized === currentLocation()) return
  if (replace) window.history.replaceState({}, '', normalized)
  else window.history.pushState({}, '', normalized)
  window.dispatchEvent(new PopStateEvent('popstate'))
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function AppLink({ href, onClick, ...props }: AnchorHTMLAttributes<HTMLAnchorElement> & { href: string }) {
  function follow(event: ReactMouseEvent<HTMLAnchorElement>) {
    onClick?.(event)
    if (
      event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey ||
      event.shiftKey || event.altKey || props.target === '_blank'
    ) return
    event.preventDefault()
    navigate(href)
  }

  return <a {...props} href={href} onClick={follow} />
}

function App() {
  const [authState, setAuthState] = useState<'loading' | 'anonymous' | 'authenticated'>('loading')
  const [user, setUser] = useState<PortalUser | null>(null)
  const [location, setLocation] = useState(currentLocation)

  useEffect(() => {
    api.me()
      .then((currentUser) => {
        setUser(currentUser)
        setAuthState('authenticated')
      })
      .catch((error: unknown) => {
        if (error instanceof ApiRequestError && error.status === 401) {
          setAuthState('anonymous')
          return
        }
        setAuthState('anonymous')
      })
  }, [])

  useEffect(() => {
    const handleNavigation = () => setLocation(currentLocation())
    window.addEventListener('popstate', handleNavigation)
    return () => window.removeEventListener('popstate', handleNavigation)
  }, [])

  if (authState === 'loading') return <LoadingScreen />
  if (authState === 'anonymous' || !user) {
    return <LoginPage onAuthenticated={(authenticatedUser) => {
      setUser(authenticatedUser)
      setAuthState('authenticated')
    }} />
  }

  const availablePaths = new Set(['/', '/sites', '/audits', '/tasks', '/users'])
  const pathname = new URL(location, window.location.origin).pathname
  const currentPath = availablePaths.has(pathname) ? pathname : '/'
  const isAdmin = user.roles.includes('ADMIN')

  const page = currentPath === '/sites'
    ? <SitesPage user={user} />
    : currentPath === '/users'
      ? <UsersPage allowed={isAdmin} />
      : currentPath === '/audits'
        ? <AuditsPage user={user} />
        : currentPath === '/tasks'
          ? <TasksPage user={user} />
          : <DashboardPage user={user} />

  return (
    <div className="app-shell">
      <Sidebar currentPath={currentPath} showUsers={isAdmin} />
      <main className="main-area">
        <Topbar user={user} onLogout={async () => {
          try {
            await api.logout()
          } finally {
            setUser(null)
            setAuthState('anonymous')
          }
        }} />
        <div className="page-frame" key={currentPath}>{page}</div>
      </main>
    </div>
  )
}

function LoadingScreen() {
  return (
    <div className="loading-screen" role="status">
      <span className="brand-mark">W</span>
      <strong>Подготавливаем рабочее пространство…</strong>
    </div>
  )
}

function LoginPage({ onAuthenticated }: { onAuthenticated: (user: PortalUser) => void }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const demoAccounts = [
    { label: 'Администратор', email: 'admin@webcare.local', password: 'WebcareAdmin!2026' },
    { label: 'Специалист', email: 'specialist@webcare.local', password: 'WebcareSpecialist!2026' },
    { label: 'Наблюдатель', email: 'viewer@webcare.local', password: 'WebcareViewer!2026' },
  ]

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      onAuthenticated(await api.login(email, password))
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-story">
        <div className="brand login-brand">
          <span className="brand-mark" aria-hidden="true">W</span>
          <span><strong>WebCare</strong><small>site monitoring</small></span>
        </div>
        <div>
          <span className="section-kicker">Единое рабочее пространство</span>
          <h1>Спокойный контроль каждого сайта.</h1>
          <p>Реестр сайтов, безопасный аудит и разграничение доступа команды собраны в одном рабочем пространстве.</p>
        </div>
        <ul className="login-benefits">
          <li><span>01</span> Роли и права проверяются на сервере</li>
          <li><span>02</span> Опасные адреса блокируются до сохранения</li>
          <li><span>03</span> Все данные остаются в PostgreSQL</li>
        </ul>
      </section>

      <section className="login-card" aria-labelledby="login-title">
        <span className="eyebrow">WebCare Portal · MVP</span>
        <h2 id="login-title">Вход в систему</h2>
        <p>Выберите одну из публичных демонстрационных ролей или введите данные вручную.</p>

        {error && <div className="alert error" role="alert">{error}</div>}

        <form onSubmit={submit} className="form-stack">
          <label>Email<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="username" required /></label>
          <label>Пароль<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required /></label>
          <button className="primary-button wide" type="submit" disabled={submitting}>
            {submitting ? 'Входим…' : 'Войти'}
          </button>
        </form>

        <div className="demo-accounts">
          <span>Демо-роли</span>
          <div>
            {demoAccounts.map((account) => (
              <button key={account.email} type="button" onClick={() => {
                setEmail(account.email)
                setPassword(account.password)
                setError('')
              }}>{account.label}</button>
            ))}
          </div>
          <small>Кнопки заполняют публичные данные выбранной роли. Затем нажмите «Войти».</small>
        </div>
      </section>
    </main>
  )
}

function Sidebar({ currentPath, showUsers }: { currentPath: string; showUsers: boolean }) {
  const navigation = showUsers
    ? [...baseNavigation, { to: '/users', label: 'Команда', mark: 'К' }]
    : baseNavigation

  return (
    <aside className="sidebar">
      <div className="brand">
        <span className="brand-mark" aria-hidden="true">W</span>
        <span><strong>WebCare</strong><small>site monitoring</small></span>
      </div>
      <nav className="primary-nav" aria-label="Основная навигация">
        {navigation.map((item) => (
          <AppLink className={`nav-item${currentPath === item.to ? ' active' : ''}`} aria-current={currentPath === item.to ? 'page' : undefined} href={item.to} key={item.to}>
            <span className="nav-mark" aria-hidden="true">{item.mark}</span>{item.label}
          </AppLink>
        ))}
      </nav>
      <div className="sidebar-footer">
        <div className="connection-state online"><span className="status-dot" aria-hidden="true" /><span>Защищённая сессия</span></div>
        <p>Мониторинг и обслуживание</p>
      </div>
    </aside>
  )
}

function Topbar({ user, onLogout }: { user: PortalUser; onLogout: () => Promise<void> }) {
  const primaryRole = user.roles[0] || 'VIEWER'
  const initials = user.displayName.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase()
  return (
    <header className="topbar">
      <div><span className="eyebrow">Рабочее пространство</span><strong>WebCare Portal</strong></div>
      <div className="profile">
        <span className="role-pill">{roleLabels[primaryRole]}</span>
        <span className="avatar" aria-hidden="true">{initials}</span>
        <span className="profile-name">{user.displayName}</span>
        <button className="logout-button" type="button" onClick={() => void onLogout()}>Выйти</button>
      </div>
    </header>
  )
}

function DashboardPage({ user }: { user: PortalUser }) {
  const [data, setData] = useState<Dashboard | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.dashboard().then(setData).catch((requestError) => setError(errorMessage(requestError)))
  }, [])

  return (
    <>
      <section className="hero-panel compact-hero">
        <div>
          <span className="section-kicker">Добро пожаловать, {user.displayName.split(' ')[0]}</span>
          <h1>Изменения сайтов теперь видны.</h1>
          <p>Сравнивайте последние проверки, разбирайте проблемы и контролируйте работу команды в одном пространстве.</p>
        </div>
        <div className="hero-aside" aria-label="Сводная оценка">
          <span>Средняя оценка</span><strong>{data?.averageScore ?? '—'}</strong><small>по последним аудитам сайтов</small>
        </div>
      </section>
      {error && <div className="alert error" role="alert">{error}</div>}
      <section className="metrics" aria-label="Ключевые показатели">
        <MetricCard label="Сайтов в реестре" value={data ? String(data.totalWebsites) : '…'} meta="" tone="green" />
        <MetricCard label="Завершённых аудитов" value={data ? String(data.completedAudits) : '…'} meta="" tone="blue" />
        <MetricCard label="Найдено сейчас" value={data ? String(data.latestFindings) : '…'} meta="" tone="amber" />
        <MetricCard label="Открытых задач" value={data ? String(data.openTasks) : '…'} meta="" tone="violet" />
      </section>
      <div className="dashboard-grid">
        <Panel title="Состояние сайтов" action="Все аудиты" actionHref="/audits">
          {!data ? <InlineLoading /> : data.sites.length === 0
            ? <EmptyState title="Реестр пока пуст" text="Добавьте сайт и выполните первую проверку." />
            : <div className="site-list">{data.sites.slice(0, 6).map((site) => <DashboardSiteRow key={site.websiteId} site={site} />)}</div>}
        </Panel>
        <Panel title="Последние проверки" action="История" actionHref="/audits">
          {!data ? <InlineLoading /> : data.recentAudits.length === 0
            ? <EmptyState title="Аудитов пока нет" text="Запустите аудит из реестра сайтов." />
            : <div className="recent-audits">{data.recentAudits.map((audit) => (
              <AppLink href={`/audits?audit=${audit.id}`} key={audit.id}><span className={`audit-state ${audit.status.toLowerCase()}`}>{auditStatusLabels[audit.status]}</span><strong>{audit.websiteName}</strong><small>{formatDate(audit.createdAt)} · {formatProblemCount(audit.findingsCount)}</small></AppLink>
            ))}</div>}
        </Panel>
      </div>
    </>
  )
}

function SitesPage({ user }: { user: PortalUser }) {
  const canManage = user.roles.some((role) => role === 'ADMIN' || role === 'SPECIALIST')
  const [data, setData] = useState<WebsitePage | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [editor, setEditor] = useState<Website | 'new' | null>(null)
  const [selected, setSelected] = useState<Website | null>(null)
  const [loadingDetails, setLoadingDetails] = useState(false)
  const [startingAuditId, setStartingAuditId] = useState('')
  const detailsRequestId = useRef(0)

  const load = useCallback(async () => {
    try {
      setData(await api.websites())
      setError('')
    } catch (requestError) {
      setError(errorMessage(requestError))
    }
  }, [])

  useEffect(() => { void load() }, [load])

  async function showDetails(id: string) {
    const requestId = ++detailsRequestId.current
    setLoadingDetails(true)
    setError('')
    try {
      const website = await api.website(id)
      if (requestId === detailsRequestId.current) setSelected(website)
    } catch (requestError) {
      if (requestId === detailsRequestId.current) setError(errorMessage(requestError))
    } finally {
      if (requestId === detailsRequestId.current) setLoadingDetails(false)
    }
  }

  async function save(input: WebsiteInput) {
    const saved = editor === 'new'
      ? await api.createWebsite(input)
      : await api.updateWebsite(editor!.id, input)
    setEditor(null)
    setSelected(saved)
    setNotice(editor === 'new' ? 'Сайт добавлен в реестр.' : 'Настройки сайта обновлены.')
    await load()
  }

  async function startAudit(website: Website) {
    setStartingAuditId(website.id)
    setError('')
    setNotice('')
    try {
      const audit = await api.startAudit(website.id)
      navigate(`/audits?audit=${audit.id}`)
    } catch (requestError) {
      setError(errorMessage(requestError))
      setStartingAuditId('')
    }
  }

  return (
    <>
      <PageHeading kicker="Реестр" title="Сайты" description="Публичные домены и безопасные границы автоматического обхода."
        action={canManage ? <button className="primary-button" type="button" onClick={() => { setEditor('new'); setNotice('') }}>Добавить сайт</button> : <span className="read-only-pill">Только просмотр</span>} />
      {notice && <div className="alert success" role="status">{notice}</div>}
      {error && <div className="alert error" role="alert">{error}</div>}
      <Panel title={data ? `Сайты · ${data.totalItems}` : 'Сайты'} className="full-panel">
        {!data ? <InlineLoading /> : data.items.length === 0
          ? <EmptyState title="В реестре нет сайтов" text={canManage ? 'Добавьте публичный HTTP или HTTPS адрес. Сервер проверит DNS и сетевую безопасность.' : 'Попросите администратора или специалиста добавить сайт.'} />
          : <div className="table-wrap"><table className="websites-table">
            <thead><tr><th>Сайт</th><th>Состояние</th><th>Лимит</th><th>Глубина</th><th>Действия</th></tr></thead>
            <tbody>{data.items.map((website) => (
              <tr key={website.id}>
                <td><strong>{website.name}</strong><small>{website.url}</small></td>
                <td><span className={`state-pill ${website.active ? 'active' : 'paused'}`}>{website.active ? 'Активен' : 'Приостановлен'}</span></td>
                <td>{website.maxPages} стр.</td><td>{website.maxDepth}</td>
                <td><div className="table-actions">
                  {canManage && <button className="audit-action" type="button" disabled={!website.active || startingAuditId === website.id} onClick={() => void startAudit(website)}>{startingAuditId === website.id ? 'Запускаем…' : 'Запустить аудит'}</button>}
                  <button type="button" onClick={() => void showDetails(website.id)}>Открыть</button>
                  {canManage && <button type="button" onClick={() => { setEditor(website); setNotice('') }}>Изменить</button>}
                </div></td>
              </tr>
            ))}</tbody>
          </table></div>}
      </Panel>
      {loadingDetails && <InlineLoading />}
      {selected && !loadingDetails && <WebsiteDetails website={selected} canManage={canManage} onEdit={() => setEditor(selected)} onClose={() => setSelected(null)} />}
      {editor && <WebsiteEditor website={editor === 'new' ? null : editor} onClose={() => setEditor(null)} onSave={save} />}
    </>
  )
}

function WebsiteDetails({ website, canManage, onEdit, onClose }: { website: Website; canManage: boolean; onEdit: () => void; onClose: () => void }) {
  return (
    <section className="detail-panel" aria-labelledby="website-details-title">
      <div className="detail-heading"><div><span className="section-kicker">Карточка сайта</span><h2 id="website-details-title">{website.name}</h2></div><button className="icon-button" type="button" onClick={onClose} aria-label="Закрыть карточку">×</button></div>
      <div className="detail-grid">
        <Detail label="Нормализованный URL" value={website.url} />
        <Detail label="Разрешённый домен" value={website.normalizedHost} />
        <Detail label="Максимум страниц" value={String(website.maxPages)} />
        <Detail label="Максимальная глубина" value={String(website.maxDepth)} />
        <Detail label="Добавил" value={website.createdBy} />
        <Detail label="Обновлён" value={formatDate(website.updatedAt)} />
      </div>
      <div className="security-note"><strong>Граница безопасности</strong><p>Обход переходит только по HTTP/HTTPS ссылкам с точным хостом <code>{website.normalizedHost}</code>. DNS проверяется повторно перед каждым сетевым обращением.</p></div>
      {canManage && <button className="secondary-button" type="button" onClick={onEdit}>Изменить настройки</button>}
    </section>
  )
}

function WebsiteEditor({ website, onClose, onSave }: { website: Website | null; onClose: () => void; onSave: (input: WebsiteInput) => Promise<void> }) {
  const [form, setForm] = useState<WebsiteInput>({
    name: website?.name || '', url: website?.url || '', maxPages: website?.maxPages || 50,
    maxDepth: website?.maxDepth ?? 2, active: website?.active ?? true,
  })
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const title = website ? 'Изменить сайт' : 'Добавить сайт'

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError('')
    try {
      const parsed = new URL(form.url)
      if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error()
    } catch {
      setError('Введите полный URL, начинающийся с http:// или https://.')
      return
    }
    setSaving(true)
    try {
      await onSave(form)
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) onClose() }}>
      <section className="modal" role="dialog" aria-modal="true" aria-labelledby="website-editor-title">
        <div className="modal-heading"><div><span className="section-kicker">Безопасный реестр</span><h2 id="website-editor-title">{title}</h2></div><button className="icon-button" type="button" onClick={onClose} aria-label="Закрыть форму">×</button></div>
        <p className="modal-intro">URL будет нормализован, разрешён через DNS и проверен на локальные, частные и служебные адреса.</p>
        {error && <div className="alert error" role="alert">{error}</div>}
        <form className="form-stack" onSubmit={submit}>
          <label>Название<input value={form.name} maxLength={160} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="Например, корпоративный сайт" required /></label>
          <label>Стартовый URL<input type="url" value={form.url} maxLength={2048} onChange={(event) => setForm({ ...form, url: event.target.value })} placeholder="https://example.com/" required /></label>
          <div className="form-grid">
            <label>Максимум страниц<input type="number" min="1" max="100" value={form.maxPages} onChange={(event) => setForm({ ...form, maxPages: Number(event.target.value) })} required /></label>
            <label>Глубина обхода<select value={form.maxDepth} onChange={(event) => setForm({ ...form, maxDepth: Number(event.target.value) })}><option value="0">0 — только стартовая</option><option value="1">1 уровень</option><option value="2">2 уровня</option><option value="3">3 уровня</option></select></label>
          </div>
          <label className="check-field"><input type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span><strong>Сайт активен</strong><small>Неактивный сайт останется в реестре, но аудит нельзя будет запустить.</small></span></label>
          <div className="modal-actions"><button className="secondary-button" type="button" onClick={onClose}>Отмена</button><button className="primary-button" type="submit" disabled={saving}>{saving ? 'Проверяем адрес…' : website ? 'Сохранить' : 'Добавить сайт'}</button></div>
        </form>
      </section>
    </div>
  )
}

function UsersPage({ allowed }: { allowed: boolean }) {
  const [users, setUsers] = useState<PortalUser[] | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    if (allowed) api.users().then(setUsers).catch((requestError) => setError(errorMessage(requestError)))
  }, [allowed])

  if (!allowed) return <AccessDenied />
  return (
    <>
      <PageHeading kicker="Доступ" title="Команда" description="Пользователи и роли, влияющие на доступ к порталу." action={<span className="read-only-pill">Управляет администратор</span>} />
      {error && <div className="alert error" role="alert">{error}</div>}
      <Panel title="Учётные записи" className="full-panel">
        {!users ? <InlineLoading /> : <div className="user-grid">{users.map((member) => (
          <article className="user-card" key={member.id}><span className="user-avatar">{member.displayName.split(/\s+/).map((part) => part[0]).slice(0, 2).join('')}</span><div><strong>{member.displayName}</strong><small>{member.email}</small></div><span className={`state-pill ${member.active ? 'active' : 'paused'}`}>{member.active ? 'Активен' : 'Отключён'}</span><div className="user-roles">{member.roles.map((role) => <span key={role}>{roleLabels[role]}</span>)}</div></article>
        ))}</div>}
      </Panel>
      <div className="permission-matrix">
        <strong>Матрица доступа</strong><span>Администратор: полный доступ и команда</span><span>Специалист: сайты, аудиты и задачи</span><span>Наблюдатель: просмотр панели, аудитов и задач</span>
      </div>
    </>
  )
}

function AuditsPage({ user }: { user: PortalUser }) {
  const requestedId = new URLSearchParams(window.location.search).get('audit') || ''
  const [data, setData] = useState<AuditPage | null>(null)
  const [selectedId, setSelectedId] = useState(requestedId)
  const [error, setError] = useState('')
  const auditsRequestId = useRef(0)

  const load = useCallback(async () => {
    const requestId = ++auditsRequestId.current
    try {
      const result = await api.audits()
      if (requestId !== auditsRequestId.current) return
      setData(result)
      setError('')
      setSelectedId((current) => current && result.items.some((audit) => audit.id === current)
        ? current
        : result.items[0]?.id || '')
    } catch (requestError) {
      if (requestId === auditsRequestId.current) setError(errorMessage(requestError))
    }
  }, [])

  useEffect(() => { void load() }, [load])
  const hasActiveAudit = data?.items.some((audit) => audit.status === 'CREATED' || audit.status === 'RUNNING') ?? false
  useEffect(() => {
    if (!hasActiveAudit) return
    const timer = window.setInterval(() => void load(), 2_000)
    return () => window.clearInterval(timer)
  }, [hasActiveAudit, load])

  const selected = data?.items.find((audit) => audit.id === selectedId) || data?.items[0]
  return (
    <>
      <PageHeading kicker="История" title="Аудиты" description="Фоновые запуски, прогресс обхода и итоговая техническая оценка." action={<AppLink className="secondary-button" href="/sites">Запустить для сайта</AppLink>} />
      {error && <div className="alert error" role="alert">{error}</div>}
      {!data ? <Panel title="Аудиты" className="full-panel"><InlineLoading /></Panel>
        : data.items.length === 0
          ? <Panel title="Аудиты" className="full-panel"><EmptyState title="Запусков пока нет" text="Откройте реестр сайтов и запустите первый безопасный аудит." /></Panel>
          : <div className="audits-layout">
            <Panel title={`История · ${data.totalItems}`} className="audit-list-panel">
              <div className="audit-list">{data.items.map((audit) => (
                <button className={`audit-list-item${selected?.id === audit.id ? ' selected' : ''}`} type="button" key={audit.id} onClick={() => setSelectedId(audit.id)}>
                  <span className={`audit-state ${audit.status.toLowerCase()}`}>{auditStatusLabels[audit.status]}</span>
                  <strong>{audit.websiteName}</strong>
                  <small>{formatDate(audit.createdAt)} · {audit.startedBy}</small>
                </button>
              ))}</div>
            </Panel>
            {selected && <AuditDetails audit={selected} canManage={user.roles.some((role) => role === 'ADMIN' || role === 'SPECIALIST')} />}
          </div>}
    </>
  )
}

const auditStatusLabels: Record<AuditStatus, string> = {
  CREATED: 'В очереди', RUNNING: 'Выполняется', COMPLETED: 'Завершён', FAILED: 'Ошибка',
}

const severityLabels: Record<FindingSeverity, string> = {
  CRITICAL: 'Критическая', HIGH: 'Высокая', MEDIUM: 'Средняя', LOW: 'Низкая', INFO: 'Информация',
}

const changeLabels: Record<FindingChangeType, string> = {
  NEW: 'Новая', ONGOING: 'Продолжается', FIXED: 'Исправлена',
}

function AuditDetails({ audit, canManage }: { audit: Audit; canManage: boolean }) {
  const isActive = audit.status === 'CREATED' || audit.status === 'RUNNING'
  const [results, setResults] = useState<AuditResults | null>(null)
  const [comparison, setComparison] = useState<AuditComparison | null>(null)
  const [severity, setSeverity] = useState<FindingSeverity | ''>('')
  const [changeType, setChangeType] = useState<FindingChangeType | ''>('')
  const [ruleCode, setRuleCode] = useState('')
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [creatingTaskId, setCreatingTaskId] = useState('')
  const resultsRequestId = useRef(0)

  const loadResults = useCallback(async () => {
    const requestId = ++resultsRequestId.current
    setLoading(true)
    try {
      const loaded = await api.auditResults(audit.id, {
        severity: severity || undefined,
        changeType: changeType || undefined,
        ruleCode: ruleCode || undefined,
        query: query || undefined,
      })
      if (requestId !== resultsRequestId.current) return
      setResults(loaded)
      setError('')
    } catch (requestError) {
      if (requestId === resultsRequestId.current) setError(errorMessage(requestError))
    } finally {
      if (requestId === resultsRequestId.current) setLoading(false)
    }
  }, [audit.id, severity, changeType, ruleCode, query])

  useEffect(() => {
    setResults(null)
    setComparison(null)
  }, [audit.id])
  useEffect(() => { void loadResults() }, [loadResults, audit.status, audit.pagesChecked, audit.findingsCount])
  useEffect(() => {
    setComparison(null)
    if (audit.status !== 'COMPLETED') return
    api.auditComparison(audit.websiteId).then(setComparison).catch(() => setComparison(null))
  }, [audit.id, audit.status, audit.websiteId])

  async function createTask(finding: Finding) {
    setCreatingTaskId(finding.id)
    setError('')
    try {
      await api.createTask(finding.id)
      await loadResults()
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setCreatingTaskId('')
    }
  }

  return (
    <section className="audit-detail" aria-live={isActive ? 'polite' : 'off'}>
      <div className="audit-detail-heading">
        <div><span className="section-kicker">Запуск {audit.id.slice(0, 8)}</span><h2>{audit.websiteName}</h2></div>
        <span className={`audit-state large ${audit.status.toLowerCase()}`}>{auditStatusLabels[audit.status]}</span>
      </div>
      <div className="audit-score-row">
        <div><span>Оценка</span><strong>{audit.score ?? (isActive ? '…' : '—')}</strong></div>
        <div><span>Проверено страниц</span><strong>{audit.pagesChecked}</strong></div>
        <div><span>Найдено</span><strong>{audit.findingsCount}</strong></div>
      </div>
      <div className="audit-meta-grid">
        <Detail label="Создан" value={formatDate(audit.createdAt)} />
        <Detail label="Начат" value={audit.startedAt ? formatDate(audit.startedAt) : 'Ожидает исполнителя'} />
        <Detail label="Завершён" value={audit.finishedAt ? formatDate(audit.finishedAt) : 'В процессе'} />
      </div>
      {isActive && <div className="audit-live-note"><span /><div><strong>Фоновый обход продолжается</strong><p>Страница обновляется автоматически каждые две секунды. Можно перейти в другой раздел — аудит не остановится.</p></div></div>}
      {audit.status === 'FAILED' && <div className="alert error" role="alert"><strong>{audit.failureCode}</strong><br />{audit.failureMessage}</div>}
      {error && <div className="alert error" role="alert">{error}</div>}
      {comparison && comparison.latestAudit.id === audit.id && <ComparisonSummary comparison={comparison} />}
      <div className="result-section-heading"><div><span className="section-kicker">Проблемы аудита</span><h3>{results ? formatProblemCount(results.filteredFindings) : 'Загрузка'}</h3></div></div>
      <div className="finding-filters" aria-label="Фильтры проблем">
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Поиск по проблемам и URL" aria-label="Поиск проблем" />
        <select value={severity} onChange={(event) => setSeverity(event.target.value as FindingSeverity | '')} aria-label="Важность">
          <option value="">Все уровни</option>{Object.entries(severityLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
        </select>
        <select value={changeType} onChange={(event) => setChangeType(event.target.value as FindingChangeType | '')} aria-label="Изменение">
          <option value="">Все изменения</option><option value="NEW">Новые</option><option value="ONGOING">Продолжающиеся</option>
        </select>
        <input value={ruleCode} onChange={(event) => setRuleCode(event.target.value)} placeholder="Код правила" aria-label="Код правила" />
        {(query || severity || changeType || ruleCode) && <button type="button" onClick={() => { setQuery(''); setSeverity(''); setChangeType(''); setRuleCode('') }}>Сбросить</button>}
      </div>
      {results && <div className="severity-strip">{Object.entries(results.severityCounts).map(([level, count]) => <span className={`severity ${level.toLowerCase()}`} key={level}>{severityLabels[level as FindingSeverity]} <strong>{count}</strong></span>)}</div>}
      {loading && !results ? <InlineLoading /> : results && results.findings.length === 0
        ? <EmptyState title="По выбранным условиям проблем нет" text="Измените фильтры или проверьте другой аудит." />
        : <div className="finding-list">{results?.findings.map((finding) => <FindingCard finding={finding} canManage={canManage} creating={creatingTaskId === finding.id} onCreate={() => void createTask(finding)} key={finding.id} />)}</div>}
      {results && <details className="pages-results"><summary>Проверенные страницы · {results.pages.length}</summary>
        {results.pages.length === 0 ? <EmptyState title="Страниц пока нет" text="Результаты появятся по мере выполнения обхода." /> : <div className="table-wrap"><table><thead><tr><th>URL</th><th>HTTP</th><th>Время</th><th>Глубина</th></tr></thead><tbody>{results.pages.map((page) => <tr key={page.id}><td><strong>{page.url}</strong>{page.errorMessage && <small>{page.errorMessage}</small>}</td><td>{page.httpStatus ?? '—'}</td><td>{page.responseTimeMs == null ? '—' : `${page.responseTimeMs} мс`}</td><td>{page.depth}</td></tr>)}</tbody></table></div>}
      </details>}
    </section>
  )
}

function ComparisonSummary({ comparison }: { comparison: AuditComparison }) {
  const hasPrevious = Boolean(comparison.previousAudit)
  const delta = comparison.scoreDelta
  return (
    <section className="comparison-summary">
      <div><span className="section-kicker">Сравнение последних аудитов</span><h3>{hasPrevious ? `${formatDate(comparison.previousAudit!.createdAt)} → ${formatDate(comparison.latestAudit.createdAt)}` : 'Первый базовый аудит'}</h3></div>
      <div className="comparison-metrics">
        <span className="change new"><strong>{comparison.newFindings.length}</strong> новых</span>
        <span className="change ongoing"><strong>{comparison.ongoingFindings.length}</strong> продолжаются</span>
        <span className="change fixed"><strong>{comparison.fixedFindings.length}</strong> исправлено</span>
        <span className={`score-delta ${(delta ?? 0) >= 0 ? 'positive' : 'negative'}`}><strong>{delta == null ? '—' : `${delta > 0 ? '+' : ''}${delta}`}</strong> к оценке</span>
      </div>
    </section>
  )
}

function FindingCard({ finding, canManage, creating, onCreate }: { finding: Finding; canManage: boolean; creating: boolean; onCreate: () => void }) {
  return <article className="finding-card">
    <div className="finding-card-top"><span className={`severity ${finding.severity.toLowerCase()}`}>{severityLabels[finding.severity]}</span><span className={`change-badge ${finding.changeType.toLowerCase()}`}>{changeLabels[finding.changeType]}</span><code>{finding.ruleCode}</code></div>
    <h4>{finding.title}</h4><a href={finding.pageUrl} target="_blank" rel="noreferrer">{finding.pageUrl}</a>
    <p>{finding.description}</p><div className="recommendation"><strong>Что сделать</strong><span>{finding.recommendation}</span></div>
    {finding.elementContext && <code className="element-context">{finding.elementContext}</code>}
    <div className="finding-actions">{finding.taskId
      ? <AppLink className="secondary-button" href={`/tasks?task=${finding.taskId}`}>Открыть задачу</AppLink>
      : canManage && <button className="primary-button" type="button" disabled={creating} onClick={onCreate}>{creating ? 'Создаём…' : 'Создать задачу'}</button>}
    </div>
  </article>
}

const taskStatusLabels: Record<TaskStatus, string> = {
  OPEN: 'Открыта', IN_PROGRESS: 'В работе', DONE: 'Выполнена', CANCELED: 'Отменена',
}

const priorityLabels: Record<TaskPriority, string> = {
  LOW: 'Низкий', MEDIUM: 'Средний', HIGH: 'Высокий', CRITICAL: 'Критический',
}

function TasksPage({ user }: { user: PortalUser }) {
  const requestedId = new URLSearchParams(window.location.search).get('task') || ''
  const canManage = user.roles.some((role) => role === 'ADMIN' || role === 'SPECIALIST')
  const [data, setData] = useState<TaskPage | null>(null)
  const [users, setUsers] = useState<PortalUser[]>([])
  const [selectedId, setSelectedId] = useState(requestedId)
  const [status, setStatus] = useState<TaskStatus | ''>('')
  const [priority, setPriority] = useState<TaskPriority | ''>('')
  const [assignedToUserId, setAssignedToUserId] = useState('')
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const tasksRequestId = useRef(0)

  const load = useCallback(async () => {
    const requestId = ++tasksRequestId.current
    setLoading(true)
    try {
      const result = await api.tasks({
        status: status || undefined,
        priority: priority || undefined,
        assignedToUserId: assignedToUserId || undefined,
        query: query || undefined,
      })
      if (requestId !== tasksRequestId.current) return
      setData(result)
      setSelectedId((current) => current && result.items.some((task) => task.id === current)
        ? current
        : result.items[0]?.id || '')
      setError('')
    } catch (requestError) {
      if (requestId === tasksRequestId.current) setError(errorMessage(requestError))
    } finally {
      if (requestId === tasksRequestId.current) setLoading(false)
    }
  }, [status, priority, assignedToUserId, query])

  useEffect(() => { void load() }, [load])
  useEffect(() => {
    if (!canManage) return
    api.users().then((members) => setUsers(members.filter((member) => member.active && member.roles.some((role) => role === 'ADMIN' || role === 'SPECIALIST')))).catch(() => setUsers([]))
  }, [canManage])

  const selected = data?.items.find((task) => task.id === selectedId) || data?.items[0]

  async function save(taskId: string, input: TaskUpdateInput) {
    setNotice('')
    setError('')
    try {
      const updated = await api.updateTask(taskId, input)
      setData((current) => current ? { ...current, items: current.items.map((item) => item.id === updated.id ? updated : item) } : current)
      setSelectedId(updated.id)
      setNotice('Изменения задачи сохранены.')
    } catch (requestError) {
      setError(errorMessage(requestError))
      throw requestError
    }
  }

  return <>
    <PageHeading kicker="Обслуживание" title="Задачи" description="Рабочая очередь по проблемам, найденным аудитами." action={<span className="read-only-pill">{canManage ? 'Редактирование доступно' : 'Только просмотр'}</span>} />
    {error && <div className="alert error" role="alert">{error}</div>}
    {notice && <div className="alert success" role="status">{notice}</div>}
    <div className="task-filters" aria-label="Фильтры задач">
      <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Поиск по задаче, сайту или URL" aria-label="Поиск задач" />
      <select value={status} onChange={(event) => setStatus(event.target.value as TaskStatus | '')}><option value="">Все статусы</option>{Object.entries(taskStatusLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select>
      <select value={priority} onChange={(event) => setPriority(event.target.value as TaskPriority | '')}><option value="">Все приоритеты</option>{Object.entries(priorityLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select>
      <select value={assignedToUserId} onChange={(event) => setAssignedToUserId(event.target.value)}><option value="">Все ответственные</option>{users.map((member) => <option value={member.id} key={member.id}>{member.displayName}</option>)}</select>
      {(query || status || priority || assignedToUserId) && <button type="button" onClick={() => { setQuery(''); setStatus(''); setPriority(''); setAssignedToUserId('') }}>Сбросить</button>}
    </div>
    {loading && !data ? <Panel title="Задачи" className="full-panel"><InlineLoading /></Panel>
      : data && data.items.length === 0 ? <Panel title="Задачи" className="full-panel"><EmptyState title="Задач не найдено" text="Создайте задачу из проблемы аудита или измените фильтры." /></Panel>
        : <div className="tasks-layout">
          <Panel title={`Очередь · ${data?.totalItems ?? 0}`} className="task-list-panel"><div className="task-list">{data?.items.map((task) => <button className={`task-list-item${selected?.id === task.id ? ' selected' : ''}`} type="button" key={task.id} onClick={() => { setSelectedId(task.id); setNotice('') }}><span className={`priority ${task.priority.toLowerCase()}`}>{priorityLabels[task.priority]}</span><strong>{task.title}</strong><small>{task.websiteName} · {task.assignedTo || 'не назначена'}</small><span className={`task-status ${task.status.toLowerCase()}`}>{taskStatusLabels[task.status]}</span></button>)}</div></Panel>
          {selected && <TaskDetails task={selected} users={users} canManage={canManage} onSave={save} />}
        </div>}
  </>
}

function TaskDetails({ task, users, canManage, onSave }: { task: MaintenanceTask; users: PortalUser[]; canManage: boolean; onSave: (id: string, input: TaskUpdateInput) => Promise<void> }) {
  const [title, setTitle] = useState(task.title)
  const [description, setDescription] = useState(task.description || '')
  const [priority, setPriority] = useState<TaskPriority>(task.priority)
  const [status, setStatus] = useState<TaskStatus>(task.status)
  const [assignee, setAssignee] = useState(task.assignedToUserId || '')
  const [dueDate, setDueDate] = useState(task.dueAt ? task.dueAt.slice(0, 10) : '')
  const [comment, setComment] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    setTitle(task.title); setDescription(task.description || ''); setPriority(task.priority); setStatus(task.status)
    setAssignee(task.assignedToUserId || ''); setDueDate(task.dueAt ? task.dueAt.slice(0, 10) : ''); setComment('')
  }, [task])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    try {
      await onSave(task.id, {
        title, description: description || undefined, priority, status,
        assignedToUserId: assignee || undefined,
        dueAt: dueDate ? new Date(`${dueDate}T12:00:00`).toISOString() : undefined,
        comment: comment || undefined,
      })
      setComment('')
    } finally {
      setSaving(false)
    }
  }

  return <section className="task-detail">
    <div className="task-detail-heading"><div><span className="section-kicker">{task.websiteName} · {task.ruleCode}</span><h2>{task.title}</h2><a href={task.pageUrl} target="_blank" rel="noreferrer">{task.pageUrl}</a></div><span className={`task-status large ${task.status.toLowerCase()}`}>{taskStatusLabels[task.status]}</span></div>
    {canManage ? <form className="task-form" onSubmit={submit}>
      <label>Название<input value={title} maxLength={300} onChange={(event) => setTitle(event.target.value)} required /></label>
      <label>Описание<textarea value={description} maxLength={4000} rows={4} onChange={(event) => setDescription(event.target.value)} /></label>
      <div className="form-grid"><label>Приоритет<select value={priority} onChange={(event) => setPriority(event.target.value as TaskPriority)}>{Object.entries(priorityLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label><label>Статус<select value={status} onChange={(event) => setStatus(event.target.value as TaskStatus)}>{Object.entries(taskStatusLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label></div>
      <div className="form-grid"><label>Ответственный<select value={assignee} onChange={(event) => setAssignee(event.target.value)}><option value="">Не назначен</option>{users.map((member) => <option value={member.id} key={member.id}>{member.displayName}</option>)}</select></label><label>Срок<input type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} /></label></div>
      <label>Комментарий к смене статуса<textarea value={comment} maxLength={1000} rows={2} onChange={(event) => setComment(event.target.value)} placeholder="Попадёт в историю, если статус изменился" /></label>
      <button className="primary-button" type="submit" disabled={saving}>{saving ? 'Сохраняем…' : 'Сохранить задачу'}</button>
    </form> : <div className="detail-grid task-readonly"><Detail label="Приоритет" value={priorityLabels[task.priority]} /><Detail label="Ответственный" value={task.assignedTo || 'Не назначен'} /><Detail label="Срок" value={task.dueAt ? formatDate(task.dueAt) : 'Не задан'} /></div>}
    <div className="task-origin"><strong>Исходная проблема</strong><p>{task.findingTitle}</p><AppLink href={`/audits?audit=${task.auditId}`}>Открыть результаты аудита</AppLink></div>
    <div className="history"><div className="result-section-heading"><div><span className="section-kicker">Журнал</span><h3>История состояний</h3></div></div>{task.history.map((entry) => <article key={entry.id}><span className="history-dot" /><div><strong>{entry.oldStatus ? `${taskStatusLabels[entry.oldStatus]} → ` : ''}{taskStatusLabels[entry.newStatus]}</strong><small>{entry.changedBy} · {formatDate(entry.changedAt)}</small>{entry.comment && <p>{entry.comment}</p>}</div></article>)}</div>
  </section>
}

function AccessDenied() {
  return <section className="empty-large"><span>403</span><h1>Раздел доступен администратору</h1><p>Ваша роль не разрешает просматривать список пользователей.</p><AppLink className="secondary-button" href="/">Вернуться к обзору</AppLink></section>
}

function PageHeading({ kicker, title, description, action }: { kicker: string; title: string; description: string; action: ReactNode }) {
  return <div className="page-heading"><div><span className="section-kicker">{kicker}</span><h1>{title}</h1><p>{description}</p></div>{action}</div>
}

function Panel({ title, action, actionHref, className = '', children }: { title: string; action?: string; actionHref?: string; className?: string; children: ReactNode }) {
  return <section className={`panel ${className}`}><div className="panel-heading"><h2>{title}</h2>{action && actionHref && <AppLink href={actionHref}>{action} <span aria-hidden="true">→</span></AppLink>}</div>{children}</section>
}

function MetricCard({ label, value, meta, tone }: { label: string; value: string; meta: string; tone: string }) {
  return <article className="metric-card"><span className={`metric-signal ${tone}`} aria-hidden="true" /><span>{label}</span><strong>{value}</strong><small>{meta}</small></article>
}

function DashboardSiteRow({ site }: { site: DashboardSite }) {
  return <AppLink className="site-row dashboard-site-row" href={site.latestAuditId ? `/audits?audit=${site.latestAuditId}` : '/sites'}><span className={`site-avatar ${site.score != null && site.score >= 80 ? 'healthy' : 'attention'}`} aria-hidden="true">{site.websiteName.slice(0, 1).toUpperCase()}</span><div className="site-name"><strong>{site.websiteName}</strong><small>{site.host}</small></div><div className="site-meta"><span>Оценка: {site.score ?? '—'}</span><small>{formatProblemCount(site.findingsCount)}</small></div><span className={`state-pill ${site.active ? 'active' : 'paused'}`}>{site.checkedAt ? formatDate(site.checkedAt) : 'Не проверен'}</span></AppLink>
}

function InlineLoading() { return <div className="inline-loading" role="status"><span />Загружаем данные…</div> }
function EmptyState({ title, text }: { title: string; text: string }) { return <div className="empty-state"><span>＋</span><strong>{title}</strong><p>{text}</p></div> }
function Detail({ label, value }: { label: string; value: string }) { return <div className="detail-item"><span>{label}</span><strong>{value}</strong></div> }

function errorMessage(error: unknown) {
  if (error instanceof ApiRequestError) return error.message
  if (error instanceof Error && error.message) return error.message
  return 'Не удалось связаться с сервером. Повторите попытку.'
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function formatProblemCount(value: number) {
  const lastTwoDigits = value % 100
  const lastDigit = value % 10
  const noun = lastTwoDigits >= 11 && lastTwoDigits <= 14
    ? 'проблем'
    : lastDigit === 1
      ? 'проблема'
      : lastDigit >= 2 && lastDigit <= 4
        ? 'проблемы'
        : 'проблем'
  return `${value} ${noun}`
}

export default App
