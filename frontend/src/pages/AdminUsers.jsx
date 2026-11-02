import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import {
  ArrowLeft, ArrowRight, Check, Eye, KeyRound, Pencil, Plus,
  RefreshCcw, ShieldCheck, Trash2, UsersRound, X,
} from 'lucide-react'
import { api } from '../api/client.js'
import { Alert, Button, Empty, PageTitle, Spinner, PasswordInput, Pagination } from '../components/Shared.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { useModalFocus } from '../utils/useModalFocus.js'

const PAGE_SIZE = 20
const emptyUser = {
  email: '', username: '', firstName: '', lastName: '', phone: '', role: 'USER', password: '',
}
const fullUserName = user => [user.firstName, user.lastName].filter(Boolean).join(' ') || user.username
const sameUser = (a, b) => a != null && b != null && String(a) === String(b)
const roleName = role => role === 'ADMIN' ? 'Администратор' : 'Пользователь'
const passwordByteLength = password => new TextEncoder().encode(password).length

function UserDialog({ mode, user, currentUserId, onClose, onSuccess, onModeChange }) {
  const isCreate = mode === 'create'
  const isEdit = mode === 'edit'
  const isSelf = sameUser(user?.id, currentUserId)
  const [data, setData] = useState(() => isCreate ? { ...emptyUser } : {
    ...emptyUser, ...user, phone: user?.phone || '', password: '',
  })
  const [step, setStep] = useState(0)
  const [newPassword, setNewPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const dialogRef = useModalFocus(true,onClose,!busy)
  const name = user ? fullUserName(user) : 'Новый пользователь'

  useEffect(() => {
    // Keep the page stationary while this modal is visible.
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = previous
    }
  }, [busy, onClose])

  const change = event => setData(current => ({ ...current, [event.target.name]: event.target.value }))
  const setFailure = message => { setError(message); setBusy(false) }

  function validateUser() {
    if (isCreate && (data.password.length < 8 || passwordByteLength(data.password) > 72)) {
      setError('Пароль должен содержать минимум 8 символов и не более 72 байт UTF-8.')
      return false
    }
    if (isSelf && data.role !== user.role) {
      setError('Администратор не может изменить собственную роль.')
      return false
    }
    return true
  }

  async function saveUser(event) {
    event.preventDefault()
    setError('')
    if (step === 0) {
      if (!validateUser()) return
      setStep(1)
      return
    }
    setBusy(true)
    try {
      if (isCreate) {
        const payload = {
          email: data.email.trim(), username: data.username.trim(),
          firstName: data.firstName.trim(), lastName: data.lastName.trim(),
          phone: data.phone.trim(), role: data.role, password: data.password,
        }
        const created = await api.adminCreateUser(payload)
        onSuccess(`Пользователь ${created.username} создан (ID ${created.id}).`, 'create')
      } else {
        const updates = {}
        for (const key of ['email', 'username', 'firstName', 'lastName', 'phone', 'role']) {
          const value = key === 'role' ? data[key] : String(data[key] ?? '').trim()
          const original = key === 'role' ? user[key] : String(user[key] ?? '').trim()
          if (value !== original) updates[key] = value
        }
        if (!Object.keys(updates).length) {
          onClose()
          return
        }
        await api.adminUpdateUser(user.id, updates)
        onSuccess(`Данные пользователя ${data.username} сохранены.`, 'edit')
      }
    } catch (err) { setFailure(err.message) }
  }

  async function resetPassword(event) {
    event.preventDefault()
    setError('')
    if (newPassword !== confirmation) return setError('Пароли не совпадают.')
    if (newPassword.length < 8 || newPassword.length > 64 || passwordByteLength(newPassword) > 72) {
      return setError('Новый пароль: от 8 до 64 символов, максимум 72 байта UTF-8.')
    }
    setBusy(true)
    try {
      await api.adminResetPassword(user.id, newPassword)
      onSuccess(`Пароль ${user.username} сброшен. При следующем входе потребуется смена пароля.`, 'reset')
    } catch (err) { setFailure(err.message) }
  }

  async function deleteUser() {
    setBusy(true)
    setError('')
    try {
      await api.adminDeleteUser(user.id)
      onSuccess(`Пользователь ${user.username} удалён.`, 'delete')
    } catch (err) { setFailure(err.message) }
  }

  const title = {
    create: 'Создание пользователя', edit: `Редактирование: ${name}`,
    view: `Пользователь: ${name}`, reset: 'Сброс пароля', delete: 'Удаление пользователя',
  }[mode]

  return createPortal(
    <div className="hotel-wizard-overlay admin-users-overlay">
      <section ref={dialogRef} tabIndex={-1} className="hotel-wizard admin-users-wizard" role="dialog" aria-modal="true" aria-label={title}>
        <header className="hotel-wizard-header">
          <div><h2>{title}</h2><p>{isCreate || isEdit ? 'Управление пользователями Booking Service' : `ID ${user.id} · ${user.username}`}</p></div>
          <button className="wizard-close" onClick={onClose} disabled={busy} type="button">Закрыть <X size={17}/></button>
        </header>
        {(isCreate || isEdit) && <div className="admin-users-steps" aria-label="Этапы мастера">
          {['Данные пользователя', 'Проверка'].map((label, index) => <div key={label} className={step === index ? 'is-current' : (index < step ? 'is-complete' : '')}>
            <span>{index < step ? <Check size={16}/> : index + 1}</span>{label}
          </div>)}
        </div>}
        <div className="hotel-wizard-body admin-users-dialog-body">
          <Alert error={error}/>
          {(isCreate || isEdit) && <form id="admin-user-form" onSubmit={saveUser} className="admin-form admin-users-form">
            {step === 0 ? <>
              <div className="admin-users-form-grid">
                <label>Имя *<input name="firstName" required maxLength={80} value={data.firstName} onChange={change} autoComplete="off"/></label>
                <label>Фамилия *<input name="lastName" required maxLength={80} value={data.lastName} onChange={change} autoComplete="off"/></label>
                <label>Имя пользователя *<input name="username" required minLength={3} maxLength={30} pattern="[A-Za-z][A-Za-z0-9._-]{2,29}" title="3–30 символов: латинские буквы, цифры, точка, дефис, подчёркивание. Первый символ — буква." value={data.username} onChange={change} autoComplete="off"/></label>
                <label>Email *<input name="email" type="email" required maxLength={255} value={data.email} onChange={change} autoComplete="off"/></label>
                <label>Телефон (необязательно)<input name="phone" type="tel" placeholder="+374XXXXXXXX" pattern={"\\+[1-9][0-9]{7,14}"} title="Международный формат: + и 8–15 цифр" value={data.phone} onChange={change} autoComplete="off"/></label>
                <label>Роль *<select name="role" value={data.role} onChange={change} disabled={isSelf} required><option value="USER">Пользователь (USER)</option><option value="ADMIN">Администратор (ADMIN)</option></select></label>
                {isCreate && <label className="admin-users-wide">Начальный пароль *<PasswordInput name="password" required minLength={8} autoComplete="new-password" value={data.password} onChange={change}/><small>Не менее 8 символов, максимум 72 байта UTF-8. Пароль не отображается при проверке.</small></label>}
              </div>
              {isSelf && <p className="admin-users-help"><ShieldCheck size={16}/> Изменение собственной роли запрещено backend.</p>}
              {isEdit && <p className="admin-users-password-note"><KeyRound size={16}/> Пароль не меняется вместе с данными профиля. Для другого пользователя используйте «Сбросить пароль». Для своего — «Мой профиль».</p>}
            </> : <div className="admin-users-review">
              <h3>Проверьте сведения перед сохранением</h3>
              <dl>
                <div><dt>Имя</dt><dd>{data.firstName} {data.lastName}</dd></div>
                <div><dt>Логин</dt><dd>{data.username}</dd></div>
                <div><dt>Email</dt><dd>{data.email}</dd></div>
                <div><dt>Телефон</dt><dd>{data.phone || 'Не указан'}</dd></div>
                <div><dt>Роль</dt><dd>{roleName(data.role)}</dd></div>
                {isCreate && <div><dt>Пароль</dt><dd>Установлен (скрыт)</dd></div>}
              </dl>
              {data.role === 'ADMIN' && <p className="admin-users-help"><ShieldCheck size={16}/> Администратор получит доступ к управлению пользователями и отелями.</p>}
            </div>}
          </form>}
          {mode === 'view' && <div className="admin-users-review"><h3>Данные пользователя</h3><dl>
            {[['ID',user.id],['Имя',fullUserName(user)],['Имя пользователя',user.username],['Email',user.email],['Телефон',user.phone || 'Не указан'],['Роль',roleName(user.role)]].map(([key,value]) => <div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}
          </dl></div>}
          {mode === 'reset' && <form id="admin-user-reset-form" className="admin-form admin-users-form" onSubmit={resetPassword}>
            <p className="admin-users-help"><KeyRound size={17}/> Укажите новый пароль для {name}. После входа с временным паролем пользователю необходимо сразу установить новый пароль.</p>
            <label>Новый пароль *<PasswordInput required minLength={8} maxLength={64} autoComplete="new-password" value={newPassword} onChange={event => setNewPassword(event.target.value)}/></label>
            <label>Повторите пароль *<PasswordInput required minLength={8} maxLength={64} autoComplete="new-password" value={confirmation} onChange={event => setConfirmation(event.target.value)}/></label>
            <small>8–64 символа, не более 72 байт UTF-8. Пароль не включается в ответы API.</small>
          </form>}
          {mode === 'delete' && <div className="admin-users-delete-warning">
            <Trash2 size={24}/><div><h3>Удалить пользователя «{name}»?</h3>
              <p>Операция необратима. Пользователя с историей бронирований удалить нельзя. Backend также запрещает удалять самого себя или последнего администратора.</p></div>
          </div>}
        </div>
        <footer className="hotel-wizard-footer admin-users-dialog-footer">
          <span>{isCreate || isEdit ? `Шаг ${step + 1} из 2` : `Пользователь #${user.id}`}</span>
          <div className="wizard-footer-actions">
            {(isCreate || isEdit) ? <>
              {isEdit && <Button variant="outline" onClick={() => onModeChange('reset', user)} disabled={busy}><KeyRound size={16}/> Сбросить пароль</Button>}
              {step === 1 && <Button variant="outline" onClick={() => {setError('');setStep(0)}} disabled={busy}><ArrowLeft size={16}/> Назад</Button>}
              <Button disabled={busy} form="admin-user-form" type="submit">{step === 0 ? 'Далее' : busy ? 'Сохраняем...' : isCreate ? 'Создать пользователя' : 'Сохранить изменения'} <ArrowRight size={16}/></Button>
            </> : mode === 'view' ? <>
              <Button variant="outline" onClick={() => onModeChange('edit',user)}><Pencil size={16}/> Изменить</Button>
              <Button onClick={() => onModeChange('reset',user)}><KeyRound size={16}/> Сбросить пароль</Button>
            </> : mode === 'reset' ? <>
              <Button variant="outline" onClick={onClose} disabled={busy}>Отмена</Button>
              <Button form="admin-user-reset-form" type="submit" disabled={busy}>{busy ? 'Сбрасываем...' : 'Сбросить пароль'}</Button>
            </> : <>
              <Button variant="outline" onClick={onClose} disabled={busy}>Отмена</Button>
              <Button variant="danger-light" disabled={busy || isSelf} onClick={deleteUser}><Trash2 size={16}/>{busy ? 'Удаляем...' : 'Удалить пользователя'}</Button>
            </>}
          </div>
        </footer>
      </section>
    </div>, document.body
  )
}

export function AdminUsers() {
  const { user: activeUser } = useAuth()
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadingUserId, setLoadingUserId] = useState(null)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [revision, setRevision] = useState(0)
  const [dialog, setDialog] = useState(null)
  const [searchId, setSearchId] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const [nameQuery, setNameQuery] = useState('')
  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    api.adminUsers({page, size: PAGE_SIZE, ...(nameQuery ? {q:nameQuery} : {})}).then(response => {
      if (!active) return
      const entries = Array.isArray(response?.content) ? response.content : []
      const maxPage = Math.max(1, Number(response?.totalPages ?? 1))
      setUsers(entries)
      setTotalPages(maxPage)
      setTotalElements(Number(response?.totalElements ?? entries.length))
      if(page >= maxPage) setPage(maxPage - 1)
    }).catch(err => {if(active){setUsers([]);setTotalElements(0);setTotalPages(1);setError(err.message)}})
      .finally(() => {if(active) setLoading(false)})
    return () => {active = false}
  }, [page, revision, nameQuery])

  const isSearching = Boolean(nameQuery)
  const visibleUsers = users
  const displayTotal = totalElements
  const displayPages = totalPages
  const waiting = loading

  async function openUser(id, mode) {
    setError('')
    setSuccess('')
    setLoadingUserId(id)
    try {
      const details = await api.adminUser(id)
      setDialog({mode, user:details})
    } catch (err) { setError(err.message) }
    finally { setLoadingUserId(null) }
  }

  const openNextDialog = (mode, user) => setDialog({ mode, user })
  const onSuccess = (message, action) => {
    setDialog(null)
    setSuccess(message)
    if (action === 'create') setPage(isSearching ? 0 : Math.floor(totalElements / PAGE_SIZE))
    else if (action === 'delete' && visibleUsers.length === 1 && page > 0) setPage(page - 1)
    setRevision(current => current + 1)
  }
  function findByName(event) {
    event.preventDefault()
    setError('')
    setSuccess('')
    setPage(0)
    setNameQuery(searchInput.trim())
  }
  function clearNameSearch(){
    setSearchInput('')
    setNameQuery('')
    setPage(0)
  }
  function findById(event) {
    event.preventDefault()
    const id = Number(searchId)
    if (!Number.isSafeInteger(id) || id < 1) {
      setError('Введите корректный ID пользователя.')
      return
    }
    openUser(id, 'view')
  }

  return <>
    <PageTitle eyebrow="ПОЛЬЗОВАТЕЛИ" title="Управление пользователями"
      subtitle="Просмотр, создание, изменение ролей и сброс паролей пользователей Booking Service."
      action={<Button onClick={() => {setError('');setSuccess('');setDialog({mode:'create',user:null})}}><Plus size={17}/> Добавить пользователя</Button>}/>
    <div className="admin-users-toolbar">
      <form onSubmit={findById} className="admin-users-id-search admin-users-search-form">
        <label htmlFor="admin-user-id-search">Поиск по ID</label>
        <div className="admin-users-search-controls">
          <input id="admin-user-id-search" type="number" min="1" step="1" value={searchId} onChange={event => setSearchId(event.target.value)} placeholder="ID пользователя" />
          <Button variant="outline" type="submit" disabled={loadingUserId != null}><Eye size={16}/> Найти</Button>
        </div>
      </form>
      <form onSubmit={findByName} className="admin-users-name-search admin-users-search-form">
        <label htmlFor="admin-user-name-search">Поиск по имени, фамилии, логину или email</label>
        <div className="admin-users-search-controls"><input id="admin-user-name-search" type="search" value={searchInput} onChange={e=>setSearchInput(e.target.value)} placeholder="Например: Анна Петрова"/>
          <Button type="submit" variant="outline">Найти</Button>
          {isSearching && <Button type="button" variant="outline" onClick={clearNameSearch}>Сбросить</Button>}
        </div>
      </form>
      <Button className="admin-users-refresh" variant="outline" onClick={() => setRevision(current => current + 1)}><RefreshCcw size={16}/> Обновить</Button>
    </div>
    <Alert error={error} success={success} onClose={() => {setError('');setSuccess('')}}/>
    {!waiting && <p className="admin-users-count">{isSearching ? 'Найдено пользователей' : 'Всего пользователей'}: <b>{displayTotal}</b></p>}
    {waiting ? <Spinner/> : visibleUsers.length ? <div className="admin-table-scroll admin-users-table-scroll"><table className="admin-table admin-users-table">
      <thead><tr><th>ID</th><th>Пользователь</th><th>Email</th><th>Телефон</th><th>Роль</th><th>Действия</th></tr></thead>
      <tbody>{visibleUsers.map(person => <tr key={person.id}>
        <td>#{person.id}</td>
        <td><b>{fullUserName(person)}</b><small>@{person.username}</small></td>
        <td className="admin-users-email">{person.email}</td>
        <td>{person.phone || '—'}</td>
        <td><span className={`admin-users-role ${person.role === 'ADMIN' ? 'is-admin' : ''}`}><ShieldCheck size={14}/>{roleName(person.role)}</span></td>
        <td><div className="admin-users-actions">
          <Button size="small" variant="outline" disabled={loadingUserId != null} onClick={() => openUser(person.id,'view')}><Eye size={15}/> Просмотр</Button>
          <Button size="small" variant="outline" disabled={loadingUserId != null} onClick={() => openUser(person.id,'edit')}><Pencil size={15}/> Изменить</Button>
          <Button size="small" variant="outline" disabled={loadingUserId != null} onClick={() => openUser(person.id,'reset')}><KeyRound size={15}/> Сбросить пароль</Button>
          <Button size="small" variant="danger-light" disabled={loadingUserId != null || sameUser(person.id,activeUser?.id)} onClick={() => openUser(person.id,'delete')} title={sameUser(person.id, activeUser?.id) ? 'Себя удалить нельзя' : 'Удалить пользователя'}><Trash2 size={15}/></Button>
        </div></td>
      </tr>)}</tbody>
    </table></div> : <Empty icon={UsersRound} title={isSearching?'Ничего не найдено':'Пользователей пока нет'} text={isSearching?'Измените запрос или сбросьте поиск.':'Добавьте первого пользователя с помощью формы администратора.'}/>}
    {!waiting && <Pagination page={page} totalPages={displayPages} onChange={setPage} className="admin-users-pagination"/>}
    {dialog && <UserDialog key={`${dialog.mode}-${dialog.user?.id || 'new'}`} {...dialog}
      currentUserId={activeUser?.id} onClose={() => setDialog(null)}
      onModeChange={openNextDialog} onSuccess={onSuccess}/>
    }
  </>
}
